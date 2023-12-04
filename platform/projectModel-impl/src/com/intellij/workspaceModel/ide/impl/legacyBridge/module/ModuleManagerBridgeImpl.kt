// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.ModuleStore
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.*
import com.intellij.openapi.module.impl.LoadedModuleDescriptionImpl
import com.intellij.openapi.module.impl.ModuleManagerEx
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.openapi.module.impl.createGrouper
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.workspace.*
import com.intellij.platform.diagnostic.telemetry.helpers.addElapsedTimeMillis
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.jps.CustomModuleEntitySource
import com.intellij.platform.workspace.jps.JpsFileDependentEntitySource
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.serialization.impl.ModulePath
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.query.entities
import com.intellij.platform.workspace.storage.query.map
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.serviceContainer.precomputeModuleLevelExtensionModel
import com.intellij.util.graph.*
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.toPath
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicLong


private val loadAllModulesTimeMs: AtomicLong = AtomicLong()
private val newModuleTimeMs: AtomicLong = AtomicLong()
private val newNonPersistentModuleTimeMs: AtomicLong = AtomicLong()
private val loadModuleTimeMs: AtomicLong = AtomicLong()
private val setUnloadedModulesTimeMs: AtomicLong = AtomicLong()
private val createModuleInstanceTimeMs: AtomicLong = AtomicLong()
private val buildModuleGraphTimeMs: AtomicLong = AtomicLong()
private val getModulesTimeMs: AtomicLong = AtomicLong()

private val LOG = logger<ModuleManagerBridgeImpl>()
private const val MODULE_BRIDGE_MAPPING_ID = "intellij.modules.bridge"

class ModuleManagerComponentBridgeInitializer : BridgeInitializer {
  override fun isEnabled(): Boolean = true

  override fun initializeBridges(project: Project, changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    (project.serviceOrNull<ModuleManager>() as? ModuleManagerBridgeImpl)?.initializeBridges(changes, builder)
  }
}

@Suppress("OVERRIDE_DEPRECATION")
@ApiStatus.Internal
abstract class ModuleManagerBridgeImpl(private val project: Project,
                                       private val coroutineScope: CoroutineScope,
                                       moduleRootListenerBridge: ModuleRootListenerBridge) : ModuleManagerEx(), Disposable {
  private val unloadedModules: MutableMap<String, UnloadedModuleDescription> = LinkedHashMap()

  init {
    // default project doesn't have modules
    if (!project.isDefault) {
      val busConnection = project.messageBus.connect(coroutineScope)
      busConnection.subscribe(WorkspaceModelTopics.CHANGED, LegacyProjectModelListenersBridge(project = project,
                                                                                              moduleModificationTracker = this,
                                                                                              moduleRootListenerBridge = moduleRootListenerBridge))
      busConnection.subscribe(WorkspaceModelTopics.CHANGED, LoadedModulesListUpdater())
      busConnection.subscribe(WorkspaceModelTopics.UNLOADED_ENTITIES_CHANGED, object : WorkspaceModelUnloadedStorageChangeListener {
        override fun changed(event: VersionedStorageChange) {
          for (change in event.getChanges(ModuleEntity::class.java).orderToRemoveReplaceAdd()) {
            change.oldEntity?.name?.let { unloadedModules.remove(it) }
            change.newEntity?.let {
              unloadedModules[it.name] = UnloadedModuleDescriptionBridge.createDescription(it)
            }
          }
        }
      })
    }
  }

  override fun dispose() {
    modules().forEach(Disposer::dispose)
  }

  protected fun modules(): Sequence<ModuleBridge> {
    return modules(entityStore.current)
  }

  override fun areModulesLoaded(): Boolean {
    return WorkspaceModelTopics.getInstance(project).modulesAreLoaded
  }

  override fun moduleDependencyComparator(): Comparator<Module> {
    return entityStore.cachedValue(dependencyComparatorValue)
  }

  override fun moduleGraph(): Graph<Module> = entityStore.cachedValue(dependencyGraphWithTestsValue)

  override fun moduleGraph(includeTests: Boolean): Graph<Module> {
    return entityStore.cachedValue(if (includeTests) dependencyGraphWithTestsValue else dependencyGraphWithoutTestsValue)
  }

  val entityStore: VersionedEntityStorage = WorkspaceModel.getInstance(project).entityStorage

  suspend fun loadModules(loadedEntities: List<ModuleEntity>,
                          unloadedEntities: List<ModuleEntity>,
                          targetBuilder: MutableEntityStorage?,
                          initializeFacets: Boolean) = loadAllModulesTimeMs.addMeasuredTimeMillis {
    val plugins = PluginManagerCore.getPluginSet().getEnabledModules()
    val corePlugin = plugins.firstOrNull { it.pluginId == PluginManagerCore.CORE_ID }
    @Suppress("OPT_IN_USAGE")
    val result = coroutineScope {
      LOG.debug { "Loading modules for ${loadedEntities.size} entities" }

      val precomputedExtensionModel = precomputeModuleLevelExtensionModel()
      val result = loadedEntities.map { moduleEntity ->
        async {
          runCatching {
            val module = blockingContext {
              createModuleInstanceWithoutCreatingComponents(moduleEntity = moduleEntity,
                                                            versionedStorage = entityStore,
                                                            diff = targetBuilder,
                                                            isNew = false,
                                                            precomputedExtensionModel = precomputedExtensionModel,
                                                            plugins = plugins,
                                                            corePlugin = corePlugin)
            }
            module.callCreateComponentsNonBlocking()
            moduleEntity to module
          }.getOrLogException(LOG)
        }
      }

      UnloadedModuleDescriptionBridge.createDescriptions(unloadedEntities).associateByTo(unloadedModules) { it.name }

      result
    }.map { it.getCompleted() }

    val modules = LinkedHashSet<ModuleBridge>(result.size)

    fun fillBuilder(builder: MutableEntityStorage) {
      val moduleMap = builder.mutableModuleMap
      for (item in result) {
        val (entity, module) = item ?: continue
        modules.add(module)
        moduleMap.addMapping(entity, module)
        (ModuleRootComponentBridge.getInstance(module).getModuleLibraryTable() as ModuleLibraryTableBridgeImpl)
          .registerModuleLibraryInstances(builder)
      }
    }

    if (targetBuilder == null) {
      (WorkspaceModel.getInstance(project) as WorkspaceModelImpl).updateProjectModelSilent("Add module mapping") { builder ->
        fillBuilder(builder)
      }
    }
    else {
      fillBuilder(targetBuilder)
    }
    // Facets that are loaded from the cache do not generate "EntityAdded" event and aren't initialized
    // We initialize the facets manually here (after modules loading).
    if (initializeFacets) {
      coroutineScope.launch(Dispatchers.EDT) {
        for (module in modules) {
          if (!module.isDisposed) {
            module.initFacets()
          }
        }
      }
    }
  }

  override fun unloadNewlyAddedModulesIfPossible(builder: MutableEntityStorage, unloadedEntityBuilder: MutableEntityStorage) {
    val currentModuleNames = HashSet<String>()
    builder.entities(ModuleEntity::class.java).mapTo(currentModuleNames) { it.name }
    unloadedEntityBuilder.entities(ModuleEntity::class.java).mapTo(currentModuleNames) { it.name }
    AutomaticModuleUnloader.getInstance(project).processNewModules(currentModuleNames, builder, unloadedEntityBuilder)
  }

  override fun getModifiableModel(): ModifiableModuleModel {
    return ModifiableModuleModelBridgeImpl(project = project, moduleManager = this,
                                           diff = MutableEntityStorage.from(entityStore.current.toSnapshot()))
  }

  fun getModifiableModel(diff: MutableEntityStorage): ModifiableModuleModel {
    return ModifiableModuleModelBridgeImpl(project = project, moduleManager = this, diff = diff, cacheStorageResult = false)
  }

  override fun newModule(filePath: String, moduleTypeId: String): Module = newModuleTimeMs.addMeasuredTimeMillis {
    incModificationCount()
    val modifiableModel = getModifiableModel()
    val module = modifiableModel.newModule(filePath, moduleTypeId)
    modifiableModel.commit()
    return@addMeasuredTimeMillis module
  }

  override fun newNonPersistentModule(moduleName: String, id: String): Module = newNonPersistentModuleTimeMs.addMeasuredTimeMillis {
    incModificationCount()
    val modifiableModel = getModifiableModel()
    val module = modifiableModel.newNonPersistentModule(moduleName, id)
    modifiableModel.commit()
    return@addMeasuredTimeMillis module
  }

  override fun getModuleDependentModules(module: Module): List<Module> = modules.filter { isModuleDependent(it, module) }

  override val unloadedModuleDescriptions: Collection<UnloadedModuleDescription>
    get() = unloadedModules.values

  override fun hasModuleGroups(): Boolean = hasModuleGroups(entityStore)

  override fun isModuleDependent(module: Module, onModule: Module): Boolean = ModuleRootManager.getInstance(module).isDependsOn(onModule)

  override val allModuleDescriptions: Collection<ModuleDescription>
    get() = (modules().map { LoadedModuleDescriptionImpl(it) } + unloadedModuleDescriptions).toList()

  override fun getModuleGroupPath(module: Module): Array<String>? = getModuleGroupPath(module, entityStore)

  override fun getModuleGrouper(model: ModifiableModuleModel?): ModuleGrouper = createGrouper(project, model)

  override fun loadModule(file: Path): Module = loadModuleTimeMs.addMeasuredTimeMillis {
    val model = getModifiableModel()
    val module = model.loadModule(file)
    model.commit()
    return@addMeasuredTimeMillis module
  }

  override fun loadModule(filePath: String): Module = loadModuleTimeMs.addMeasuredTimeMillis {
    val model = getModifiableModel()
    val module = model.loadModule(filePath)
    model.commit()
    return@addMeasuredTimeMillis module
  }

  override fun getUnloadedModuleDescription(moduleName: String): UnloadedModuleDescription? = unloadedModules[moduleName]

  private val modulesArrayValue = CachedValue<Array<Module>> { storage ->
    modules(storage).toList().toTypedArray()
  }

  private val moduleNamesQuery = entities<ModuleEntity>().map { it.name }

  override val modules: Array<Module>
    get() = entityStore.cachedValue(modulesArrayValue)

  private val sortedModulesValue = CachedValue { storage ->
    val allModules = modules(storage).toList().toTypedArray<Module>()
    Arrays.sort(allModules, moduleDependencyComparator())
    allModules
  }

  override val sortedModules: Array<Module>
    get() = entityStore.cachedValue(sortedModulesValue)

  override fun findModuleByName(name: String): Module? {
    return entityStore.current.resolve(ModuleId(name))?.findModule(entityStore.current)
  }

  override fun disposeModule(module: Module) = ApplicationManager.getApplication().runWriteAction {
    val modifiableModel = getModifiableModel()
    modifiableModel.disposeModule(module)
    modifiableModel.commit()
  }

  override suspend fun setUnloadedModules(unloadedModuleNames: List<String>) = setUnloadedModulesTimeMs.addMeasuredTimeMillis {
    // optimization
    /* if (unloadedModules.keys == unloadedModuleNames) {
       return
     }*/

    UnloadedModulesListStorage.getInstance(project).setUnloadedModuleNames(unloadedModuleNames)

    val unloadedModulesNameHolder = UnloadedModulesListStorage.getInstance(project).unloadedModuleNameHolder
    val mainStorage = entityStore.current
    val moduleEntitiesToUnload = mainStorage.entities(ModuleEntity::class.java)
      .filter { unloadedModulesNameHolder.isUnloaded(it.name) }
      .toList()
    val unloadedEntityStorage = WorkspaceModel.getInstance(project).currentSnapshotOfUnloadedEntities
    val moduleEntitiesToLoad = unloadedEntityStorage.entities(ModuleEntity::class.java)
      .filter { !unloadedModulesNameHolder.isUnloaded(it.name) }
      .toList()

    if (unloadedModuleNames.isNotEmpty()) {
      val moduleNames = if (useNewWorkspaceModelApi()) {
        project.workspaceModel.currentSnapshot.cached(moduleNamesQuery).asSequence()
      }
      else {
        modules.asSequence().map { it.name }
      }
      val loadedModules = moduleNames.filter { it !in unloadedModuleNames }.toMutableList()
      moduleEntitiesToLoad.mapTo(loadedModules) { it.name }
      AutomaticModuleUnloader.getInstance(project).setLoadedModules(loadedModules)
    }
    else {
      AutomaticModuleUnloader.getInstance(project).setLoadedModules(emptyList())
    }

    unloadedModules.keys.removeAll { !unloadedModulesNameHolder.isUnloaded(it) }

    // we need to save module configurations before unloading, otherwise their settings will be lost
    if (moduleEntitiesToUnload.isNotEmpty()) {
      blockingContext {
        project.save()
      }
    }

    withContext(Dispatchers.EDT) {
      ApplicationManager.getApplication().runWriteAction {
        ProjectRootManagerEx.getInstanceEx(project).withRootsChange(RootsChangeRescanningInfo.NO_RESCAN_NEEDED).use {
          WorkspaceModel.getInstance(project).updateProjectModel("Update unloaded modules") { builder ->
            addAndRemoveModules(builder, moduleEntitiesToLoad, moduleEntitiesToUnload, unloadedEntityStorage)
          }
          WorkspaceModel.getInstance(project).updateUnloadedEntities("Update unloaded modules") { builder ->
            addAndRemoveModules(builder, moduleEntitiesToUnload, moduleEntitiesToLoad, mainStorage)
          }
        }
      }
    }
  }

  private fun addAndRemoveModules(builder: MutableEntityStorage,
                                  entitiesToAdd: List<ModuleEntity>,
                                  entitiesToRemove: List<ModuleEntity>,
                                  storageContainingEntitiesToAdd: EntityStorage) {
    for (entity in entitiesToRemove) {
      builder.removeEntity(entity)
    }
    for (entity in entitiesToAdd) {
      builder.addEntity(entity)
      entity.getModuleLevelLibraries(storageContainingEntitiesToAdd).forEach { libraryEntity ->
        builder.addEntity(libraryEntity)
      }
    }
  }

  override fun setUnloadedModulesSync(unloadedModuleNames: List<String>) {
    if (!ApplicationManager.getApplication().isDispatchThread) {
      @Suppress("RAW_RUN_BLOCKING")
      return runBlocking(CoreProgressManager.getCurrentThreadProgressModality().asContextElement()) {
        setUnloadedModules(unloadedModuleNames)
      }
    }

    ProgressManager.getInstance().runProcessWithProgressSynchronously(Runnable {
      val modalityState = CoreProgressManager.getCurrentThreadProgressModality()
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking(modalityState.asContextElement()) {
        setUnloadedModules(unloadedModuleNames)
      }
    }, "", true, project)
  }

  override fun removeUnloadedModules(unloadedModules: Collection<UnloadedModuleDescription>) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    unloadedModules.forEach { this.unloadedModules.remove(it.name) }

    UnloadedModulesListStorage.getInstance(project).setUnloadedModuleNames(this.unloadedModules.keys)
    WorkspaceModel.getInstance(project).updateUnloadedEntities("Remove unloaded modules") { builder ->
      val namesToRemove = unloadedModules.mapTo(HashSet()) { it.name }
      val entitiesToRemove = builder.entities(ModuleEntity::class.java).filter { it.name in namesToRemove }.toList()
      for (moduleEntity in entitiesToRemove) {
        builder.removeEntity(moduleEntity)
      }
    }
  }

  private fun createModuleInstanceWithoutCreatingComponents(
    moduleEntity: ModuleEntity,
    versionedStorage: VersionedEntityStorage,
    diff: MutableEntityStorage?,
    isNew: Boolean,
    precomputedExtensionModel: PrecomputedExtensionModel?,
    plugins: List<IdeaPluginDescriptorImpl>,
    corePlugin: IdeaPluginDescriptorImpl?,
  ): ModuleBridge {
    val moduleFileUrl = getModuleVirtualFileUrl(moduleEntity)

    val module = createModule(
      symbolicId = moduleEntity.symbolicId,
      name = moduleEntity.name,
      virtualFileUrl = moduleFileUrl,
      entityStorage = versionedStorage,
      diff = diff
    )

    module.registerComponents(
      corePlugin = corePlugin,
      modules = plugins,
      app = ApplicationManager.getApplication(),
      precomputedExtensionModel = precomputedExtensionModel,
      listenerCallbacks = null
    )

    if (moduleFileUrl == null) {
      registerNonPersistentModuleStore(module)
    }
    else {
      val moduleStore = module.getService(IComponentStore::class.java) as ModuleStore
      moduleStore.setPath(path = moduleFileUrl.toPath(), virtualFile = null, isNew = isNew)
    }
    return module
  }

  fun createModuleInstance(
    moduleEntity: ModuleEntity,
    versionedStorage: VersionedEntityStorage,
    diff: MutableEntityStorage?,
    isNew: Boolean,
    precomputedExtensionModel: PrecomputedExtensionModel?,
    plugins: List<IdeaPluginDescriptorImpl>,
    corePlugin: IdeaPluginDescriptorImpl?,
  ): ModuleBridge = createModuleInstanceTimeMs.addMeasuredTimeMillis {
    val module = createModuleInstanceWithoutCreatingComponents(moduleEntity = moduleEntity,
                                                               versionedStorage = versionedStorage,
                                                               diff = diff,
                                                               isNew = isNew,
                                                               precomputedExtensionModel = precomputedExtensionModel,
                                                               plugins = plugins,
                                                               corePlugin = corePlugin)
    module.callCreateComponents()
    return@addMeasuredTimeMillis module
  }

  open fun registerNonPersistentModuleStore(module: ModuleBridge) {}

  abstract fun loadModuleToBuilder(moduleName: String, filePath: String, diff: MutableEntityStorage): ModuleEntity

  abstract fun createModule(
    symbolicId: ModuleId,
    name: String,
    virtualFileUrl: VirtualFileUrl?,
    entityStorage: VersionedEntityStorage,
    diff: MutableEntityStorage?,
  ): ModuleBridge

  abstract fun initializeBridges(event: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage)

  private inner class LoadedModulesListUpdater : WorkspaceModelChangeListener {
    override fun changed(event: VersionedStorageChange) {
      if (event.getChanges(ModuleEntity::class.java).isNotEmpty() && unloadedModules.isNotEmpty()) {
        val moduleNames = if (useNewWorkspaceModelApi()) {
          event.storageAfter.cached(moduleNamesQuery).toList()
        }
        else {
          modules.map { it.name }
        }
        AutomaticModuleUnloader.getInstance(project).setLoadedModules(moduleNames)
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ModuleManagerBridgeImpl {
      return ModuleManager.getInstance(project) as ModuleManagerBridgeImpl
    }

    @JvmStatic
    val EntityStorage.moduleMap: ExternalEntityMapping<ModuleBridge>
      get() = getExternalMapping(MODULE_BRIDGE_MAPPING_ID)

    @JvmStatic
    val MutableEntityStorage.mutableModuleMap: MutableExternalEntityMapping<ModuleBridge>
      get() = getMutableExternalMapping(MODULE_BRIDGE_MAPPING_ID)

    fun fireModulesAdded(project: Project, modules: List<Module>) {
      project.messageBus.syncPublisher(ModuleListener.TOPIC).modulesAdded(project, modules)
    }

    internal fun getModuleGroupPath(module: Module, entityStorage: VersionedEntityStorage): Array<String>? {
      val moduleEntity = (module as ModuleBridge).findModuleEntity(entityStorage.current) ?: return null
      return moduleEntity.groupPath?.path?.toTypedArray()
    }

    internal fun getModulePath(module: Module, entityStorage: VersionedEntityStorage): ModulePath {
      return ModulePath(
        path = module.moduleFilePath,
        group = getModuleGroupPath(module, entityStorage)?.joinToString(separator = MODULE_GROUP_SEPARATOR)
      )
    }

    internal fun hasModuleGroups(entityStorage: VersionedEntityStorage): Boolean {
      return entityStorage.current.entities(ModuleGroupPathEntity::class.java).firstOrNull() != null
    }

    private val dependencyGraphWithTestsValue = CachedValue { storage ->
      buildModuleGraph(storage, true)
    }
    private val dependencyGraphWithoutTestsValue = CachedValue { storage ->
      buildModuleGraph(storage, false)
    }
    private val dependencyComparatorValue = CachedValue { storage ->
      DFSTBuilder(buildModuleGraph(storage, true)).comparator()
    }

    fun List<EntityChange<LibraryEntity>>.filterModuleLibraryChanges(): List<EntityChange<LibraryEntity>> = filter { it.isModuleLibrary() }

    private fun EntityChange<LibraryEntity>.isModuleLibrary(): Boolean {
      return when (this) {
        is EntityChange.Added -> entity.tableId is LibraryTableId.ModuleLibraryTableId
        is EntityChange.Removed -> entity.tableId is LibraryTableId.ModuleLibraryTableId
        is EntityChange.Replaced -> oldEntity.tableId is LibraryTableId.ModuleLibraryTableId
      }
    }

    @JvmStatic
    fun changeModuleEntitySource(
      module: ModuleBridge,
      moduleEntityStore: EntityStorage,
      newSource: EntitySource,
      moduleDiff: MutableEntityStorage?,
    ) {
      val oldEntitySource = module.findModuleEntity(moduleEntityStore)?.entitySource ?: return
      fun changeSources(diffBuilder: MutableEntityStorage, storage: EntityStorage) {
        val entitiesMap = storage.entitiesBySource { it == oldEntitySource }
        entitiesMap.values.asSequence().flatMap { it.values.asSequence().flatten() }.forEach {
          if (it !is FacetEntity) {
            diffBuilder.modifyEntity(WorkspaceEntity.Builder::class.java, it) {
              this.entitySource = newSource
            }
          }
        }
      }

      if (moduleDiff != null) {
        changeSources(moduleDiff, moduleEntityStore)
      }
      else {
        WriteAction.runAndWait<RuntimeException> {
          WorkspaceModel.getInstance(module.project).updateProjectModel("Change module entity source") { builder ->
            changeSources(builder, builder)
          }
        }
      }
    }

    fun getModuleVirtualFileUrl(moduleEntity: ModuleEntity): VirtualFileUrl? {
      return getImlFileDirectory(moduleEntity)?.append("${moduleEntity.name}.iml")
    }

    fun getImlFileDirectory(moduleEntity: ModuleEntity): VirtualFileUrl? {
      val entitySource = when (val moduleSource = moduleEntity.entitySource) {
        is JpsFileDependentEntitySource -> moduleSource.originalSource
        is CustomModuleEntitySource -> moduleSource.internalSource
        else -> moduleEntity.entitySource
      }
      if (entitySource !is JpsProjectFileEntitySource.FileInDirectory) {
        return null
      }
      return entitySource.directory
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}

private fun buildModuleGraph(storage: EntityStorage, includeTests: Boolean): Graph<Module> = buildModuleGraphTimeMs.addMeasuredTimeMillis {
  val moduleGraph = GraphGenerator.generate(CachingSemiGraph.cache(object : InboundSemiGraph<Module> {
    override fun getNodes(): Collection<Module> = modules(storage).toList()

    override fun getIn(m: Module): Iterator<Module> {
      val moduleMap = storage.moduleMap
      val entity = moduleMap.getFirstEntity(m as ModuleBridge) as ModuleEntity?
      return (entity?.dependencies?.asSequence() ?: emptySequence())
        .filterIsInstance<ModuleDependencyItem.Exportable.ModuleDependency>()
        .filter { includeTests || it.scope != ModuleDependencyItem.DependencyScope.TEST }
        .mapNotNull {
          it.module.resolve(storage)
        }
        .mapNotNull { moduleMap.getDataByEntity(it) }
        .iterator()
    }
  }))

  return@addMeasuredTimeMillis moduleGraph
}

private fun modules(storage: EntityStorage): Sequence<ModuleBridge> = getModulesTimeMs.addMeasuredTimeMillis {
  val moduleMap = storage.moduleMap
  return@addMeasuredTimeMillis storage.entities(ModuleEntity::class.java).mapNotNull { moduleMap.getDataByEntity(it) }
}

private fun setupOpenTelemetryReporting(meter: Meter) {
  val loadAllModulesTimeCounter = meter.counterBuilder("workspaceModel.moduleManagerBridge.load.all.modules.ms").buildObserver()
  val newModuleTimeCounter = meter.counterBuilder("workspaceModel.moduleManagerBridge.newModule.ms").buildObserver()
  val newNonPersistentModuleTimeCounter = meter.counterBuilder("workspaceModel.moduleManagerBridge.new.nonPersistent.module.ms").buildObserver()
  val loadModuleTimeCounter = meter.counterBuilder("workspaceModel.moduleManagerBridge.load.module.ms").buildObserver()
  val setUnloadedModulesTimeCounter = meter.counterBuilder("workspaceModel.moduleManagerBridge.set.unloadedModules.ms").buildObserver()
  val createModuleInstanceTimeCounter = meter.counterBuilder("workspaceModel.moduleManagerBridge.create.module.instance.ms").buildObserver()
  val buildModuleGraphTimeCounter = meter.counterBuilder("workspaceModel.moduleManagerBridge.build.module.graph.ms").buildObserver()
  val getModulesTimeCounter = meter.counterBuilder("workspaceModel.moduleManagerBridge.get.modules.ms").buildObserver()

  meter.batchCallback(
    {
      loadAllModulesTimeCounter.record(loadAllModulesTimeMs.get())
      newModuleTimeCounter.record(newModuleTimeMs.get())
      newNonPersistentModuleTimeCounter.record(newNonPersistentModuleTimeMs.get())
      loadModuleTimeCounter.record(loadModuleTimeMs.get())
      setUnloadedModulesTimeCounter.record(setUnloadedModulesTimeMs.get())
      createModuleInstanceTimeCounter.record(createModuleInstanceTimeMs.get())
      buildModuleGraphTimeCounter.record(buildModuleGraphTimeMs.get())
      getModulesTimeCounter.record(getModulesTimeMs.get())
    },
    loadAllModulesTimeCounter, newModuleTimeCounter, newNonPersistentModuleTimeCounter, loadModuleTimeCounter,
    setUnloadedModulesTimeCounter, createModuleInstanceTimeCounter, buildModuleGraphTimeCounter, getModulesTimeCounter
  )
}
