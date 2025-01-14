package twilightforest;

import com.google.common.collect.Maps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.DataPackRegistryEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import twilightforest.advancements.TFAdvancements;
import twilightforest.capabilities.CapabilityList;
import twilightforest.client.ClientInitiator;
import twilightforest.command.TFCommand;
import twilightforest.compat.curios.CuriosCompat;
import twilightforest.compat.top.TopCompat;
import twilightforest.item.recipe.UncraftingTableCondition;
import twilightforest.loot.modifiers.GiantToolGroupingModifier;
import twilightforest.network.UpdateGamerulePacket;
import twilightforest.util.MagicPaintingVariant;
import twilightforest.util.Restriction;
import twilightforest.data.custom.stalactites.entry.Stalactite;
import twilightforest.dispenser.TFDispenserBehaviors;
import twilightforest.init.*;
import twilightforest.init.custom.*;
import twilightforest.network.TFPacketHandler;
import twilightforest.util.WoodPalette;
import twilightforest.world.components.BiomeGrassColors;
import twilightforest.world.components.biomesources.LandmarkBiomeSource;
import twilightforest.world.components.biomesources.TFBiomeProvider;
import twilightforest.world.components.chunkgenerators.ChunkGeneratorTwilight;

import java.util.Locale;

@Mod(TwilightForestMod.ID)
public class TwilightForestMod {

	public static final String ID = "twilightforest";
	public static final String REGISTRY_NAMESPACE = "twilight";

	private static final String MODEL_DIR = "textures/model/";
	private static final String GUI_DIR = "textures/gui/";
	private static final String ENVIRO_DIR = "textures/environment/";
	// odd one out, as armor textures are a stringy mess at present
	public static final String ARMOR_DIR = ID + ":textures/armor/";

	public static final GameRules.Key<GameRules.BooleanValue> ENFORCED_PROGRESSION_RULE = GameRules.register("tfEnforcedProgression", GameRules.Category.UPDATES, GameRules.BooleanValue.create(true, (server, enforced) -> TFPacketHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new UpdateGamerulePacket(enforced.get())))); //Putting it in UPDATES since other world stuff is here

	public static final Logger LOGGER = LogManager.getLogger(ID);

	private static final Rarity rarity = Rarity.create("TWILIGHT", ChatFormatting.DARK_GREEN);

	public TwilightForestMod() {
		{
			final Pair<TFConfig.Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(TFConfig.Common::new);
			ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, specPair.getRight());
			TFConfig.COMMON_CONFIG = specPair.getLeft();
		}
		{
			final Pair<TFConfig.Client, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(TFConfig.Client::new);
			ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, specPair.getRight());
			TFConfig.CLIENT_CONFIG = specPair.getLeft();
		}

		DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientInitiator::call);
		MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
		MinecraftForge.EVENT_BUS.addGenericListener(Level.class, CapabilityList::attachLevelCapability);
		MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, CapabilityList::attachEntityCapability);
		MinecraftForge.EVENT_BUS.addListener(Stalactite::reloadStalactites);

		IEventBus modbus = FMLJavaModLoadingContext.get().getModEventBus();

		TFBannerPatterns.BANNER_PATTERNS.register(modbus);
		TFBlockEntities.BLOCK_ENTITIES.register(modbus);
		TFBlocks.BLOCKS.register(modbus);
		TFLoot.CONDITIONS.register(modbus);
		TFMenuTypes.CONTAINERS.register(modbus);
		TFEnchantments.ENCHANTMENTS.register(modbus);
		TFEntities.ENTITIES.register(modbus);
		BiomeLayerTypes.BIOME_LAYER_TYPES.register(modbus);
		BiomeLayerStack.BIOME_LAYER_STACKS.register(modbus);
		TFFeatures.FEATURES.register(modbus);
		TFFeatureModifiers.FOLIAGE_PLACERS.register(modbus);
		TFLoot.FUNCTIONS.register(modbus);
		TFItems.ITEMS.register(modbus);
		TFLootModifiers.LOOT_MODIFIERS.register(modbus);
		TFMobEffects.MOB_EFFECTS.register(modbus);
		TFParticleType.PARTICLE_TYPES.register(modbus);
		TFPOITypes.POIS.register(modbus);
		TFFeatureModifiers.PLACEMENT_MODIFIERS.register(modbus);
		TFRecipes.RECIPE_SERIALIZERS.register(modbus);
		TFRecipes.RECIPE_TYPES.register(modbus);
		TFSounds.SOUNDS.register(modbus);
		TFEntities.SPAWN_EGGS.register(modbus);
		TFStats.STATS.register(modbus);
		TFStructurePieceTypes.STRUCTURE_PIECE_TYPES.register(modbus);
		TFStructureProcessors.STRUCTURE_PROCESSORS.register(modbus);
		TFStructurePlacementTypes.STRUCTURE_PLACEMENT_TYPES.register(modbus);
		TFStructureTypes.STRUCTURE_TYPES.register(modbus);
		TFCreativeTabs.TABS.register(modbus);
		TFFeatureModifiers.TREE_DECORATORS.register(modbus);
		TFFeatureModifiers.TRUNK_PLACERS.register(modbus);

		DwarfRabbitVariant.DWARF_RABBITS.register(modbus);
		TinyBirdVariant.TINY_BIRDS.register(modbus);
		WoodPalettes.WOOD_PALETTES.register(modbus);
		Enforcement.ENFORCEMENTS.register(modbus);
		Restrictions.RESTRICTIONS.register(modbus);
		MagicPaintingVariants.MAGIC_PAINTINGS.register(modbus);

		modbus.addListener(this::sendIMCs);
		modbus.addListener(this::init);
		modbus.addListener(this::registerExtraStuff);
		modbus.addListener(this::setRegistriesForDatapack);
		modbus.addListener(CapabilityList::registerCapabilities);

		if (ModList.get().isLoaded("curios")) {
			Bindings.getForgeBus().get().addListener(CuriosCompat::keepCurios);
			modbus.addListener(CuriosCompat::registerCurioRenderers);
			modbus.addListener(CuriosCompat::registerCurioLayers);
		}

		BiomeGrassColors.init();
	}

	public void setRegistriesForDatapack(DataPackRegistryEvent.NewRegistry event) {
		event.dataPackRegistry(WoodPalettes.WOOD_PALETTE_TYPE_KEY, WoodPalette.CODEC);
		event.dataPackRegistry(BiomeLayerStack.BIOME_STACK_KEY, BiomeLayerStack.DISPATCH_CODEC);
		event.dataPackRegistry(Restrictions.RESTRICTION_KEY, Restriction.CODEC, Restriction.CODEC);
		event.dataPackRegistry(MagicPaintingVariants.REGISTRY_KEY, MagicPaintingVariant.CODEC, MagicPaintingVariant.CODEC);
	}

	public void registerExtraStuff(RegisterEvent evt) {
		if (evt.getRegistryKey().equals(Registries.BIOME_SOURCE)) {
			Registry.register(BuiltInRegistries.BIOME_SOURCE, TwilightForestMod.prefix("twilight_biomes"), TFBiomeProvider.TF_CODEC);
			Registry.register(BuiltInRegistries.BIOME_SOURCE, TwilightForestMod.prefix("landmarks"), LandmarkBiomeSource.CODEC);
		} else if (evt.getRegistryKey().equals(Registries.CHUNK_GENERATOR)) {
			Registry.register(BuiltInRegistries.CHUNK_GENERATOR, TwilightForestMod.prefix("structure_locating_wrapper"), ChunkGeneratorTwilight.CODEC);
		} else if (evt.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS)) {
			CraftingHelper.register(UncraftingTableCondition.Serializer.INSTANCE);
		}
	}

	public void sendIMCs(InterModEnqueueEvent evt) {
		if (ModList.get().isLoaded("theoneprobe")) {
			InterModComms.sendTo("theoneprobe", "getTheOneProbe", TopCompat::new);
		}
	}

	public void init(FMLCommonSetupEvent evt) {
		TFPacketHandler.init();
		TFAdvancements.init();

		evt.enqueueWork(() -> {
			TFSounds.registerParrotSounds();
			TFDispenserBehaviors.init();
			TFStats.init();

			CauldronInteraction.WATER.put(TFItems.ARCTIC_HELMET.get(), CauldronInteraction.DYED_ITEM);
			CauldronInteraction.WATER.put(TFItems.ARCTIC_CHESTPLATE.get(), CauldronInteraction.DYED_ITEM);
			CauldronInteraction.WATER.put(TFItems.ARCTIC_LEGGINGS.get(), CauldronInteraction.DYED_ITEM);
			CauldronInteraction.WATER.put(TFItems.ARCTIC_BOOTS.get(), CauldronInteraction.DYED_ITEM);

			AxeItem.STRIPPABLES = Maps.newHashMap(AxeItem.STRIPPABLES);
			AxeItem.STRIPPABLES.put(TFBlocks.TWILIGHT_OAK_LOG.get(), TFBlocks.STRIPPED_TWILIGHT_OAK_LOG.get());
			AxeItem.STRIPPABLES.put(TFBlocks.CANOPY_LOG.get(), TFBlocks.STRIPPED_CANOPY_LOG.get());
			AxeItem.STRIPPABLES.put(TFBlocks.MANGROVE_LOG.get(), TFBlocks.STRIPPED_MANGROVE_LOG.get());
			AxeItem.STRIPPABLES.put(TFBlocks.DARK_LOG.get(), TFBlocks.STRIPPED_DARK_LOG.get());
			AxeItem.STRIPPABLES.put(TFBlocks.TIME_LOG.get(), TFBlocks.STRIPPED_TIME_LOG.get());
			AxeItem.STRIPPABLES.put(TFBlocks.TRANSFORMATION_LOG.get(), TFBlocks.STRIPPED_TRANSFORMATION_LOG.get());
			AxeItem.STRIPPABLES.put(TFBlocks.MINING_LOG.get(), TFBlocks.STRIPPED_MINING_LOG.get());
			AxeItem.STRIPPABLES.put(TFBlocks.SORTING_LOG.get(), TFBlocks.STRIPPED_SORTING_LOG.get());

			AxeItem.STRIPPABLES.put(TFBlocks.TWILIGHT_OAK_WOOD.get(), TFBlocks.STRIPPED_TWILIGHT_OAK_WOOD.get());
			AxeItem.STRIPPABLES.put(TFBlocks.CANOPY_WOOD.get(), TFBlocks.STRIPPED_CANOPY_WOOD.get());
			AxeItem.STRIPPABLES.put(TFBlocks.MANGROVE_WOOD.get(), TFBlocks.STRIPPED_MANGROVE_WOOD.get());
			AxeItem.STRIPPABLES.put(TFBlocks.DARK_WOOD.get(), TFBlocks.STRIPPED_DARK_WOOD.get());
			AxeItem.STRIPPABLES.put(TFBlocks.TIME_WOOD.get(), TFBlocks.STRIPPED_TIME_WOOD.get());
			AxeItem.STRIPPABLES.put(TFBlocks.TRANSFORMATION_WOOD.get(), TFBlocks.STRIPPED_TRANSFORMATION_WOOD.get());
			AxeItem.STRIPPABLES.put(TFBlocks.MINING_WOOD.get(), TFBlocks.STRIPPED_MINING_WOOD.get());
			AxeItem.STRIPPABLES.put(TFBlocks.SORTING_WOOD.get(), TFBlocks.STRIPPED_SORTING_WOOD.get());

			FlowerPotBlock pot = (FlowerPotBlock) Blocks.FLOWER_POT;

			pot.addPlant(TFBlocks.TWILIGHT_OAK_SAPLING.getId(), TFBlocks.POTTED_TWILIGHT_OAK_SAPLING);
			pot.addPlant(TFBlocks.CANOPY_SAPLING.getId(), TFBlocks.POTTED_CANOPY_SAPLING);
			pot.addPlant(TFBlocks.MANGROVE_SAPLING.getId(), TFBlocks.POTTED_MANGROVE_SAPLING);
			pot.addPlant(TFBlocks.DARKWOOD_SAPLING.getId(), TFBlocks.POTTED_DARKWOOD_SAPLING);
			pot.addPlant(TFBlocks.HOLLOW_OAK_SAPLING.getId(), TFBlocks.POTTED_HOLLOW_OAK_SAPLING);
			pot.addPlant(TFBlocks.RAINBOW_OAK_SAPLING.getId(), TFBlocks.POTTED_RAINBOW_OAK_SAPLING);
			pot.addPlant(TFBlocks.TIME_SAPLING.getId(), TFBlocks.POTTED_TIME_SAPLING);
			pot.addPlant(TFBlocks.TRANSFORMATION_SAPLING.getId(), TFBlocks.POTTED_TRANSFORMATION_SAPLING);
			pot.addPlant(TFBlocks.MINING_SAPLING.getId(), TFBlocks.POTTED_MINING_SAPLING);
			pot.addPlant(TFBlocks.SORTING_SAPLING.getId(), TFBlocks.POTTED_SORTING_SAPLING);
			pot.addPlant(TFBlocks.MAYAPPLE.getId(), TFBlocks.POTTED_MAYAPPLE);
			pot.addPlant(TFBlocks.FIDDLEHEAD.getId(), TFBlocks.POTTED_FIDDLEHEAD);
			pot.addPlant(TFBlocks.MUSHGLOOM.getId(), TFBlocks.POTTED_MUSHGLOOM);
			pot.addPlant(TFBlocks.BROWN_THORNS.getId(), TFBlocks.POTTED_THORN);
			pot.addPlant(TFBlocks.GREEN_THORNS.getId(), TFBlocks.POTTED_GREEN_THORN);
			pot.addPlant(TFBlocks.BURNT_THORNS.getId(), TFBlocks.POTTED_DEAD_THORN);

			FireBlock fireblock = (FireBlock) Blocks.FIRE;
			fireblock.setFlammable(TFBlocks.ROOT_BLOCK.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.ARCTIC_FUR_BLOCK.get(), 20, 20);
			fireblock.setFlammable(TFBlocks.LIVEROOT_BLOCK.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.EMPTY_CANOPY_BOOKSHELF.get(), 30, 20);
			fireblock.setFlammable(TFBlocks.DEATH_TOME_SPAWNER.get(), 30, 20);
			fireblock.setFlammable(TFBlocks.TWILIGHT_OAK_WOOD.get(), 5, 5);
			fireblock.setFlammable(TFBlocks.TWILIGHT_OAK_PLANKS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.TWILIGHT_OAK_SLAB.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.TWILIGHT_OAK_STAIRS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.TWILIGHT_OAK_FENCE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.TWILIGHT_OAK_GATE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.CANOPY_WOOD.get(), 5, 5);
			fireblock.setFlammable(TFBlocks.CANOPY_PLANKS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.CANOPY_SLAB.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.CANOPY_STAIRS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.CANOPY_FENCE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.CANOPY_GATE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.CANOPY_BOOKSHELF.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.MANGROVE_WOOD.get(), 5, 5);
			fireblock.setFlammable(TFBlocks.MANGROVE_PLANKS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.MANGROVE_SLAB.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.MANGROVE_STAIRS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.MANGROVE_FENCE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.MANGROVE_GATE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.MANGROVE_ROOT.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.DARK_WOOD.get(), 5, 5);
			fireblock.setFlammable(TFBlocks.DARK_PLANKS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.DARK_SLAB.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.DARK_STAIRS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.DARK_FENCE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.DARK_GATE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.TIME_WOOD.get(), 5, 5);
			fireblock.setFlammable(TFBlocks.TIME_PLANKS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.TIME_SLAB.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.TIME_STAIRS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.TIME_FENCE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.TIME_GATE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.TRANSFORMATION_WOOD.get(), 5, 5);
			fireblock.setFlammable(TFBlocks.TRANSFORMATION_PLANKS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.TRANSFORMATION_SLAB.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.TRANSFORMATION_STAIRS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.TRANSFORMATION_FENCE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.TRANSFORMATION_GATE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.MINING_WOOD.get(), 5, 5);
			fireblock.setFlammable(TFBlocks.MINING_PLANKS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.MINING_SLAB.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.MINING_STAIRS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.MINING_FENCE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.MINING_GATE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.SORTING_WOOD.get(), 5, 5);
			fireblock.setFlammable(TFBlocks.SORTING_PLANKS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.SORTING_SLAB.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.SORTING_STAIRS.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.SORTING_FENCE.get(), 5, 20);
			fireblock.setFlammable(TFBlocks.SORTING_GATE.get(), 5, 20);

			ComposterBlock.add(0.1F, TFBlocks.FALLEN_LEAVES.get());
			ComposterBlock.add(0.3F, TFBlocks.CANOPY_LEAVES.get());
			ComposterBlock.add(0.3F, TFBlocks.CLOVER_PATCH.get());
			ComposterBlock.add(0.3F, TFBlocks.DARK_LEAVES.get());
			ComposterBlock.add(0.3F, TFBlocks.FIDDLEHEAD.get());
			ComposterBlock.add(0.3F, TFBlocks.HEDGE.get());
			ComposterBlock.add(0.3F, TFBlocks.MANGROVE_LEAVES.get());
			ComposterBlock.add(0.3F, TFBlocks.MAYAPPLE.get());
			ComposterBlock.add(0.3F, TFBlocks.MINING_LEAVES.get());
			ComposterBlock.add(0.3F, TFBlocks.TWILIGHT_OAK_LEAVES.get());
			ComposterBlock.add(0.3F, TFBlocks.RAINBOW_OAK_LEAVES.get());
			ComposterBlock.add(0.3F, TFBlocks.ROOT_STRAND.get());
			ComposterBlock.add(0.3F, TFBlocks.SORTING_LEAVES.get());
			ComposterBlock.add(0.3F, TFBlocks.THORN_LEAVES.get());
			ComposterBlock.add(0.3F, TFBlocks.TIME_LEAVES.get());
			ComposterBlock.add(0.3F, TFBlocks.TRANSFORMATION_LEAVES.get());
			ComposterBlock.add(0.3F, TFBlocks.TWILIGHT_OAK_SAPLING.get());
			ComposterBlock.add(0.3F, TFBlocks.CANOPY_SAPLING.get());
			ComposterBlock.add(0.3F, TFBlocks.MANGROVE_SAPLING.get());
			ComposterBlock.add(0.3F, TFBlocks.DARKWOOD_SAPLING.get());
			ComposterBlock.add(0.3F, TFBlocks.RAINBOW_OAK_SAPLING.get());
			ComposterBlock.add(0.5F, TFBlocks.BEANSTALK_LEAVES.get());
			ComposterBlock.add(0.5F, TFBlocks.MOSS_PATCH.get());
			ComposterBlock.add(0.5F, TFBlocks.ROOT_BLOCK.get());
			ComposterBlock.add(0.5F, TFBlocks.THORN_ROSE.get());
			ComposterBlock.add(0.5F, TFBlocks.TROLLVIDR.get());
			ComposterBlock.add(0.5F, TFBlocks.HOLLOW_OAK_SAPLING.get());
			ComposterBlock.add(0.5F, TFBlocks.TIME_SAPLING.get());
			ComposterBlock.add(0.5F, TFBlocks.TRANSFORMATION_SAPLING.get());
			ComposterBlock.add(0.5F, TFBlocks.MINING_SAPLING.get());
			ComposterBlock.add(0.5F, TFBlocks.SORTING_SAPLING.get());
			ComposterBlock.add(0.5F, TFBlocks.TORCHBERRY_PLANT.get());
			ComposterBlock.add(0.65F, TFBlocks.HUGE_MUSHGLOOM_STEM.get());
			ComposterBlock.add(0.65F, TFBlocks.HUGE_WATER_LILY.get());
			ComposterBlock.add(0.65F, TFBlocks.LIVEROOT_BLOCK.get());
			ComposterBlock.add(0.65F, TFBlocks.MUSHGLOOM.get());
			ComposterBlock.add(0.65F, TFBlocks.UBEROUS_SOIL.get());
			ComposterBlock.add(0.65F, TFBlocks.HUGE_STALK.get());
			ComposterBlock.add(0.65F, TFBlocks.UNRIPE_TROLLBER.get());
			ComposterBlock.add(0.65F, TFBlocks.TROLLBER.get());
			ComposterBlock.add(0.85F, TFBlocks.HUGE_LILY_PAD.get());
			ComposterBlock.add(0.85F, TFBlocks.HUGE_MUSHGLOOM.get());

			//eh, we'll do items here too
			ComposterBlock.add(0.3F, TFItems.TORCHBERRIES.get());
			ComposterBlock.add(0.5F, TFItems.LIVEROOT.get());
			ComposterBlock.add(0.65F, TFItems.MAZE_WAFER.get());
			ComposterBlock.add(0.85F, TFItems.EXPERIMENT_115.get());
			ComposterBlock.add(0.85F, TFItems.MAGIC_BEANS.get());

			GiantToolGroupingModifier.CONVERSIONS.put(Blocks.COBBLESTONE, TFBlocks.GIANT_COBBLESTONE.get().asItem());
			GiantToolGroupingModifier.CONVERSIONS.put(Blocks.OAK_LOG, TFBlocks.GIANT_LOG.get().asItem());
			GiantToolGroupingModifier.CONVERSIONS.put(Blocks.OAK_LEAVES, TFBlocks.GIANT_LEAVES.get().asItem());
			GiantToolGroupingModifier.CONVERSIONS.put(Blocks.OBSIDIAN, TFBlocks.GIANT_OBSIDIAN.get().asItem());
		});
	}

	public void registerCommands(RegisterCommandsEvent event) {
		TFCommand.register(event.getDispatcher());
	}

	public static ResourceLocation prefix(String name) {
		return new ResourceLocation(ID, name.toLowerCase(Locale.ROOT));
	}

	public static ResourceLocation namedRegistry(String name) {
		return new ResourceLocation(REGISTRY_NAMESPACE, name.toLowerCase(Locale.ROOT));
	}

	public static ResourceLocation getModelTexture(String name) {
		return new ResourceLocation(ID, MODEL_DIR + name);
	}

	public static ResourceLocation getGuiTexture(String name) {
		return new ResourceLocation(ID, GUI_DIR + name);
	}

	public static ResourceLocation getEnvTexture(String name) {
		return new ResourceLocation(ID, ENVIRO_DIR + name);
	}

	public static Rarity getRarity() {
		return rarity;
	}
}
