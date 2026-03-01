// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.AutomaticModuleUnloader
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleDescription
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.UnloadedModuleDescription
import com.intellij.openapi.module.impl.LoadedModuleDescriptionImpl
import com.intellij.openapi.module.impl.ModuleManagerEx
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.openapi.module.impl.createGrouper
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.workspace.BridgeInitializer
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.backend.workspace.WorkspaceModelUnloadedStorageChangeListener
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.backend.workspace.useNewWorkspaceModelApiForUnloadedModules
import com.intellij.platform.backend.workspace.useQueryCacheWorkspaceModelApi
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.util.coroutines.mapNotNullConcurrent
import com.intellij.platform.workspace.jps.CustomModuleEntitySource
import com.intellij.platform.workspace.jps.JpsFileDependentEntitySource
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleGroupPathEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.groupPath
import com.intellij.platform.workspace.jps.serialization.impl.ModulePath
import com.intellij.platform.workspace.storage.CachedValue
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.ExternalEntityMapping
import com.intellij.platform.workspace.storage.ExternalMappingKey
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.MutableExternalEntityMapping
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.createEntityTreeCopy
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.query.entities
import com.intellij.platform.workspace.storage.query.map
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.serviceContainer.executeRegisterTaskForOldContent
import com.intellij.serviceContainer.precomputeModuleLevelExtensionModel
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.graph.CachingSemiGraph
import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.Graph
import com.intellij.util.graph.GraphGenerator
import com.intellij.util.graph.InboundSemiGraph
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleStore
import com.intellij.workspaceModel.ide.toPath
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap

private val loadAllModulesTimeMs = MillisecondsMeasurer()
private val newModuleTimeMs = MillisecondsMeasurer()
private val newNonPersistentModuleTimeMs = MillisecondsMeasurer()
private val loadModuleTimeMs = MillisecondsMeasurer()
private val setUnloadedModulesTimeMs = MillisecondsMeasurer()
private val createModuleInstanceTimeMs = MillisecondsMeasurer()
private val buildModuleGraphTimeMs = MillisecondsMeasurer()
private val getModulesTimeMs = MillisecondsMeasurer()

private val LOG = logger<ModuleManagerBridgeImpl>()
private val MODULE_BRIDGE_MAPPING_ID = ExternalMappingKey.create<ModuleBridge>("intellij.modules.bridge")

internal class ModuleManagerComponentBridgeInitializer : BridgeInitializer {
  override fun isEnabled(): Boolean = true

  override fun initializeBridges(project: Project, changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    (project.serviceOrNull<ModuleManager>() as? ModuleManagerBridgeImpl)?.initializeBridges(changes, builder)
  }
}

@Suppress("OVERRIDE_DEPRECATION")
@ApiStatus.Internal
abstract class ModuleManagerBridgeImpl(
  private val project: Project,
  @JvmField protected val coroutineScope: CoroutineScope,
  moduleRootListenerBridge: ModuleRootListenerBridge,
) : ModuleManagerEx(), Disposable {
  private val moduleNameToUnloadedModuleDescription: MutableMap<String, UnloadedModuleDescription> = ConcurrentHashMap()

  private val moduleNamesQuery = entities<ModuleEntity>().map { it.name }

  init {
    // default project doesn't have modules
    if (!project.isDefault) {
      val busConnection = project.messageBus.connect(coroutineScope)
      busConnection.subscribe(WorkspaceModelTopics.CHANGED, LegacyProjectModelListenersBridge(project = project,
                                                                                              moduleModificationTracker = this,
                                                                                              moduleRootListenerBridge = moduleRootListenerBridge))

      if (useNewWorkspaceModelApiForUnloadedModules()) {
        // [Alex Plate] DO NOT TURN IT ON unless refactored
        // This function requires an empty list in case there are no unloaded modules, or a list of
        //   loaded modules otherwise. While it's easy to get a list of loaded modules from the workspace model,
        //   the list of unloaded modules, or just the fact that the system has unloaded modules, can be obtained only from
        //   unloaded entity storage.
        //
        // At the moment, `setLoadedModules` function is called from different places. It works fine with blocking API, but it causes
        //   racing when using async API like here.
        //   For example, we can set the list of loaded modules A and B, then unload some module A. For proper work, we should firstly call
        //   setLoadedModules(emptyList), then setLoadedModules(B).
        //   However, if the API is async, the coroutine may delay and we'll firstly call for setLoadedModules(B), and
        //   then setLoadedModules(emptyList).
        // With the new API, the proper fix would be to have all information in one storage to avoid synchronization issues. However, at the
        //   moment it's not possible to store unloaded modules under the entity storage. However, for this calculation we only need to know
        //   if there exist at least one unloaded modules. So, we can create a flag entity in workspace model that will exist if any
        //   of the modules are unloaded. This flag will be used for reactive calculation of the loaded list.
        //
        // Current problems that prevent this implementation:
        // - Such calculation will requre two entities, however at the moment we can fetch only an entity of a single type
        // - `setUnloadedModules` logic has some additional logic between `setLoadedModules` and updating the workspace model. This might
        //   be a problem.
        coroutineScope.launch {
          (project.workspaceModel as WorkspaceModelInternal).flowOfQuery(moduleNamesQuery).collect {
            delay(1000) // TODO: Get rid of it, but don't forget to test with it
            if (moduleNameToUnloadedModuleDescription.isNotEmpty()) {
              AutomaticModuleUnloader.getInstance(project).setLoadedModules(it)
            }
          }
        }
      }
      else {
        busConnection.subscribe(WorkspaceModelTopics.CHANGED, LoadedModulesListUpdater())
      }

      busConnection.subscribe(WorkspaceModelTopics.UNLOADED_ENTITIES_CHANGED, object : WorkspaceModelUnloadedStorageChangeListener {
        override fun changed(event: VersionedStorageChange) {
          for (change in event.getChanges(ModuleEntity::class.java)) {
            change.oldEntity?.name?.let { moduleNameToUnloadedModuleDescription.remove(it) }
            change.newEntity?.let {
              moduleNameToUnloadedModuleDescription[it.name] = UnloadedModuleDescriptionBridge.createDescription(it)
            }
          }
        }
      })
    }
  }

  private inner class LoadedModulesListUpdater : WorkspaceModelChangeListener {
    override fun changed(event: VersionedStorageChange) {
      if (event.getChanges(ModuleEntity::class.java).isNotEmpty() && moduleNameToUnloadedModuleDescription.isNotEmpty()) {
        val moduleNames = if (useQueryCacheWorkspaceModelApi()) {
          event.storageAfter.cached(moduleNamesQuery).toList()
        }
        else {
          modules.map { it.name }
        }
        project.service<AutomaticModuleUnloader>().setLoadedModules(moduleNames)
      }
    }
  }


  override fun dispose() {
    modules().forEach(Disposer::dispose)
  }

  @ApiStatus.Internal
  fun modules(): Sequence<ModuleBridge> {
    return modules(entityStore.current)
  }

  final override fun moduleDependencyComparator(): Comparator<Module> {
    return entityStore.cachedValue(dependencyComparatorValue)
  }

  final override fun moduleGraph(): Graph<Module> = entityStore.cachedValue(dependencyGraphWithTestsValue)

  final override fun moduleGraph(includeTests: Boolean): Graph<Module> {
    return entityStore.cachedValue(if (includeTests) dependencyGraphWithTestsValue else dependencyGraphWithoutTestsValue)
  }

  @JvmField
  val entityStore: VersionedEntityStorage = (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).entityStorage

  suspend fun loadModules(
    loadedEntities: List<ModuleEntity>,
    unloadedEntities: List<ModuleEntity>,
    targetBuilder: MutableEntityStorage?,
    initializeFacets: Boolean,
    globalWsmAppliedToProjectWsm: CompletableDeferred<Project>?,
  ): Unit = loadAllModulesTimeMs.addMeasuredTime {
    LOG.debug { "Loading modules for ${loadedEntities.size} entities: [${loadedEntities.joinToString { it.name }}]" }

    val result = createModuleBridges(loadedEntities, targetBuilder)

    if (unloadedEntities.isNotEmpty()) {
      UnloadedModuleDescriptionBridge.createDescriptions(unloadedEntities).associateByTo(moduleNameToUnloadedModuleDescription) { it.name }
    }

    val projectRootManager = project.serviceAsync<ProjectRootManager>()

    fun fillBuilder(builder: MutableEntityStorage) {
      val moduleMap = builder.mutableModuleMap
      for ((entity, module) in result) {
        moduleMap.addMapping(entity, module)
        ((projectRootManager.getModuleRootManager(module) as ModuleRootComponentBridge).getModuleLibraryTable() as ModuleLibraryTableBridgeImpl)
          .registerModuleLibraryInstances(builder)
      }
    }

    if (targetBuilder == null) {
      (project.serviceAsync<WorkspaceModel>() as WorkspaceModelImpl).updateProjectModelSilent("Add module mapping") { builder ->
        fillBuilder(builder)
      }
    }
    else {
      fillBuilder(targetBuilder)
    }
    // Facets that are loaded from the cache do not generate "EntityAdded" event and aren't initialized
    // We initialize the facets manually here (after modules loading).
    if (initializeFacets) {
      initFacets(result, globalWsmAppliedToProjectWsm)
    }

    coroutineScope.launch {
      checkModuleLevelServiceAndExtensionRegistration()
    }
  }

  private suspend fun createModuleBridges(
    loadedEntities: List<ModuleEntity>,
    targetBuilder: MutableEntityStorage?,
  ): Collection<Pair<ModuleEntity, ModuleBridge>> {
    val precomputedExtensionModel = precomputeModuleLevelExtensionModel()
    // Persistent modules perform disk loading eagerly to catch file-not-found errors during initialization
    // rather than at a random later time (see setPath). Therefore, we perform the loading concurrently.
    return loadedEntities.mapNotNullConcurrent { moduleEntity ->
      runCatching {
        val module = createModuleInstanceWithoutCreatingComponents(
          moduleEntity = moduleEntity,
          versionedStorage = entityStore,
          diff = targetBuilder,
          isNew = false,
          precomputedExtensionModel = precomputedExtensionModel,
        )
        moduleEntity to module
      }.getOrLogException(LOG)
    }
  }

  protected open fun initFacets(modules: Collection<Pair<ModuleEntity, ModuleBridge>>, globalWsmAppliedToProjectWsm: CompletableDeferred<Project>?) {
  }

  final override fun calculateUnloadModules(
    builder: MutableEntityStorage,
    unloadedEntityBuilder: MutableEntityStorage,
  ): Pair<List<String>, List<String>> {
    val currentModuleNames = HashSet<String>()
    builder.entities(ModuleEntity::class.java).mapTo(currentModuleNames) { it.name }
    unloadedEntityBuilder.entities(ModuleEntity::class.java).mapTo(currentModuleNames) { it.name }
    return project.service<AutomaticModuleUnloader>().calculateNewModules(currentModuleNames, builder, unloadedEntityBuilder)
  }

  final override fun updateUnloadedStorage(modulesToLoad: List<String>, modulesToUnload: List<String>) {
    project.service<AutomaticModuleUnloader>().updateUnloadedStorage(modulesToLoad, modulesToUnload)
  }

  final override fun getModifiableModel(): ModifiableModuleModel {
    return ModifiableModuleModelBridgeImpl(
      project = project,
      moduleManager = this,
      diff = MutableEntityStorage.from(entityStore.current.toSnapshot()),
    )
  }

  fun getModifiableModel(diff: MutableEntityStorage): ModifiableModuleModel {
    return ModifiableModuleModelBridgeImpl(project = project, moduleManager = this, diff = diff, cacheStorageResult = false)
  }

  @RequiresWriteLock
  override fun newModule(filePath: String, moduleTypeId: String): Module = newModuleTimeMs.addMeasuredTime {
    incModificationCount()
    val modifiableModel = getModifiableModel()
    val module = modifiableModel.newModule(filePath, moduleTypeId)
    modifiableModel.commit()
    return@addMeasuredTime module
  }

  override fun newNonPersistentModule(moduleName: String, id: String): Module = newNonPersistentModuleTimeMs.addMeasuredTime {
    incModificationCount()
    val modifiableModel = getModifiableModel()
    val module = modifiableModel.newNonPersistentModule(moduleName, id)
    modifiableModel.commit()
    return@addMeasuredTime module
  }

  override fun getModuleDependentModules(module: Module): List<Module> = modules.filter { isModuleDependent(it, module) }

  override val unloadedModuleDescriptions: Collection<UnloadedModuleDescription>
    get() = moduleNameToUnloadedModuleDescription.values

  override fun hasModuleGroups(): Boolean = hasModuleGroups(entityStore)

  override fun isModuleDependent(module: Module, onModule: Module): Boolean = ModuleRootManager.getInstance(module).isDependsOn(onModule)

  override val allModuleDescriptions: Collection<ModuleDescription>
    get() = (modules().map { LoadedModuleDescriptionImpl(it) } + unloadedModuleDescriptions).toList()

  override fun getModuleGroupPath(module: Module): Array<String>? = getModuleGroupPath(module, entityStore)

  override fun getModuleGrouper(model: ModifiableModuleModel?): ModuleGrouper = createGrouper(project, model)

  override fun loadModule(file: Path): Module = loadModuleTimeMs.addMeasuredTime {
    val model = getModifiableModel()
    val module = model.loadModule(file)
    model.commit()
    return@addMeasuredTime module
  }

  override fun loadModule(filePath: String): Module = loadModuleTimeMs.addMeasuredTime {
    val model = getModifiableModel()
    val module = model.loadModule(filePath)
    model.commit()
    return@addMeasuredTime module
  }

  override fun getUnloadedModuleDescription(moduleName: String): UnloadedModuleDescription? = moduleNameToUnloadedModuleDescription[moduleName]

  private val modulesArrayValue = CachedValue<Array<Module>> { storage ->
    modules(storage).toList().toTypedArray()
  }

  override val modules: Array<Module>
    get() = entityStore.cachedValue(modulesArrayValue)

  private val sortedModulesValue = CachedValue { storage ->
    val allModules = modules(storage).toList().toTypedArray<Module>()
    Arrays.sort(allModules, moduleDependencyComparator())
    allModules
  }

  override val sortedModules: Array<Module>
    get() = entityStore.cachedValue(sortedModulesValue)

  private val modulesByNameMapValue = CachedValue<Map<String, Module>> { storage ->
    modules(storage).associateByTo(hashMapOf()) { it.name }
  }

  override val modulesByNameMap: Map<String, Module>
    get() = entityStore.cachedValue(modulesByNameMapValue)

  override fun findModuleByName(name: String): Module? {
    return entityStore.current.resolve(ModuleId(name))?.findModule(entityStore.current)
  }

  override fun disposeModule(module: Module) {
    ApplicationManager.getApplication().runWriteAction {
      val modifiableModel = getModifiableModel()
      modifiableModel.disposeModule(module)
      modifiableModel.commit()
    }
  }

  final override suspend fun setUnloadedModules(unloadedModuleNames: List<String>): Unit = setUnloadedModulesTimeMs.addMeasuredTime {
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
    val unloadedEntityStorage = (project.serviceAsync<WorkspaceModel>() as WorkspaceModelInternal).currentSnapshotOfUnloadedEntities
    val moduleEntitiesToLoad = unloadedEntityStorage.entities(ModuleEntity::class.java)
      .filter { !unloadedModulesNameHolder.isUnloaded(it.name) }
      .toList()

    if (unloadedModuleNames.isNotEmpty()) {
      val moduleNames = if (useQueryCacheWorkspaceModelApi()) {
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

    moduleNameToUnloadedModuleDescription.keys.removeAll { !unloadedModulesNameHolder.isUnloaded(it) }

    // we need to save module configurations before unloading, otherwise their settings will be lost
    if (moduleEntitiesToUnload.isNotEmpty()) {
      project.save()
    }

    val workspaceModel = project.serviceAsync<WorkspaceModel>() as WorkspaceModelInternal
    withContext(Dispatchers.EDT) {
      edtWriteAction {
        ProjectRootManagerEx.getInstanceEx(project).withRootsChange(RootsChangeRescanningInfo.NO_RESCAN_NEEDED).use {
          workspaceModel.updateProjectModel("Update unloaded modules") { builder ->
            addAndRemoveModules(builder, moduleEntitiesToLoad, moduleEntitiesToUnload, unloadedEntityStorage)
          }
          workspaceModel.updateUnloadedEntities("Update unloaded modules") { builder ->
            addAndRemoveModules(builder, moduleEntitiesToUnload, moduleEntitiesToLoad, mainStorage)
          }
        }
      }
    }
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  private fun addAndRemoveModules(
    builder: MutableEntityStorage,
    entitiesToAdd: List<ModuleEntity>,
    entitiesToRemove: List<ModuleEntity>,
    storageContainingEntitiesToAdd: EntityStorage,
  ) {
    for (entity in entitiesToRemove) {
      builder.removeEntity(entity)
    }
    for (entity in entitiesToAdd) {
      builder.addEntity(entity.createEntityTreeCopy(true))
      entity.getModuleLevelLibraries(storageContainingEntitiesToAdd).forEach { libraryEntity ->
        builder.addEntity(libraryEntity.createEntityTreeCopy(true))
      }
    }
  }

  @Suppress("UsagesOfObsoleteApi", "DEPRECATION")
  final override fun setUnloadedModulesSync(unloadedModuleNames: List<String>) {
    if (!ApplicationManager.getApplication().isDispatchThread) {
      @Suppress("RAW_RUN_BLOCKING", "DEPRECATION")
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

  final override fun removeUnloadedModules(unloadedModules: Collection<UnloadedModuleDescription>) {
    ThreadingAssertions.assertWriteAccess()

    unloadedModules.forEach { this.moduleNameToUnloadedModuleDescription.remove(it.name) }

    UnloadedModulesListStorage.getInstance(project).setUnloadedModuleNames(this.moduleNameToUnloadedModuleDescription.keys)
    (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).updateUnloadedEntities("Remove unloaded modules") { builder ->
      val namesToRemove = unloadedModules.mapTo(HashSet()) { it.name }
      val entitiesToRemove = builder.entities(ModuleEntity::class.java).filter { it.name in namesToRemove }.toList()
      for (moduleEntity in entitiesToRemove) {
        builder.removeEntity(moduleEntity)
      }
    }
  }

  protected fun createModuleInstanceWithoutCreatingComponents(
    moduleEntity: ModuleEntity,
    versionedStorage: VersionedEntityStorage,
    diff: MutableEntityStorage?,
    isNew: Boolean,
    precomputedExtensionModel: PrecomputedExtensionModel,
  ): ModuleBridge {
    val moduleFileUrl = getModuleVirtualFileUrl(moduleEntity)
    return createModule(
      symbolicId = moduleEntity.symbolicId,
      name = moduleEntity.name,
      virtualFileUrl = moduleFileUrl,
      entityStorage = versionedStorage,
      diff = diff,
    ) { module ->
      module.initServiceContainer(precomputedExtensionModel)
      if (moduleFileUrl != null) {
        val moduleStore = module.componentStore as ModuleStore
        moduleStore.setPath(path = moduleFileUrl.toPath(), isNew = isNew)
      }
      module.markContainerAsCreated()
    }
  }

  internal fun createModuleInstance(
    moduleEntity: ModuleEntity,
    versionedStorage: VersionedEntityStorage,
    diff: MutableEntityStorage?,
    isNew: Boolean,
    precomputedExtensionModel: PrecomputedExtensionModel,
  ): ModuleBridge = createModuleInstanceTimeMs.addMeasuredTime {
    val module = createModuleInstanceWithoutCreatingComponents(
      moduleEntity = moduleEntity,
      versionedStorage = versionedStorage,
      diff = diff,
      isNew = isNew,
      precomputedExtensionModel = precomputedExtensionModel,
    )
    return@addMeasuredTime module
  }

  abstract fun loadModuleToBuilder(moduleName: String, filePath: String, diff: MutableEntityStorage): ModuleEntity

  abstract fun createModule(
    symbolicId: ModuleId,
    name: String,
    virtualFileUrl: VirtualFileUrl?,
    entityStorage: VersionedEntityStorage,
    diff: MutableEntityStorage?,
    init: (ModuleBridge) -> Unit,
  ): ModuleBridge

  abstract fun initializeBridges(event: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage)

  companion object {
    fun getInstance(project: Project): ModuleManagerBridgeImpl {
      return ModuleManager.getInstance(project) as ModuleManagerBridgeImpl
    }

    val EntityStorage.moduleMap: ExternalEntityMapping<ModuleBridge>
      get() = getExternalMapping(MODULE_BRIDGE_MAPPING_ID)

    val MutableEntityStorage.mutableModuleMap: MutableExternalEntityMapping<ModuleBridge>
      get() = getMutableExternalMapping(MODULE_BRIDGE_MAPPING_ID)

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
        is EntityChange.Added -> newEntity.tableId is LibraryTableId.ModuleLibraryTableId
        is EntityChange.Removed -> oldEntity.tableId is LibraryTableId.ModuleLibraryTableId
        is EntityChange.Replaced -> oldEntity.tableId is LibraryTableId.ModuleLibraryTableId
      }
    }

    fun changeModuleEntitySource(
      module: ModuleBridge,
      moduleEntityStore: EntityStorage,
      newSource: EntitySource,
      moduleDiff: MutableEntityStorage?,
    ) {
      val oldEntitySource = module.findModuleEntity(moduleEntityStore)?.entitySource ?: return
      fun changeSources(diffBuilder: MutableEntityStorage, storage: EntityStorage) {
        val entitiesMap = storage.entitiesBySource { it == oldEntitySource }
        entitiesMap.forEach {
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

private fun checkModuleLevelServiceAndExtensionRegistration() {
  val plugins = PluginManagerCore.getPluginSet().enabledPlugins
  for (plugin in plugins) {
    for (content in plugin.contentModules) {
      checkModuleLevel(plugin = plugin, child = content, forbid = false)
    }

    executeRegisterTaskForOldContent(plugin) {
      checkModuleLevel(plugin = plugin, child = it, forbid = true)
    }
  }
}

private fun checkModuleLevel(plugin: IdeaPluginDescriptorImpl, child: IdeaPluginDescriptorImpl, forbid: Boolean) {
  fun check(list: List<*>, asWarn: Boolean = false) {
    if (list.isNotEmpty()) {
      val message = "Plugin $plugin is trying to register $list in a content module ($child). " +
                    "Module-level services and extensions are deprecated, and support is scheduled to be removed."
      if (!asWarn || forbid) {
        LOG.warn(message)
      }
      else {
        LOG.debug(message)
      }
    }
  }

  check(child.moduleContainerDescriptor.services, asWarn = true)
  check(child.moduleContainerDescriptor.components)
  check(child.moduleContainerDescriptor.extensionPoints)
  check(child.moduleContainerDescriptor.listeners)
}

private fun buildModuleGraph(storage: EntityStorage, includeTests: Boolean): Graph<Module> = buildModuleGraphTimeMs.addMeasuredTime {
  val moduleGraph = GraphGenerator.generate(CachingSemiGraph.cache(object : InboundSemiGraph<Module> {
    override fun getNodes(): Collection<Module> = modules(storage).toList()

    override fun getIn(m: Module): Iterator<Module> {
      val moduleMap = storage.moduleMap
      val entity = moduleMap.getFirstEntity(m as ModuleBridge) as ModuleEntity?
      return (entity?.dependencies?.asSequence() ?: emptySequence())
        .filterIsInstance<ModuleDependency>()
        .filter { includeTests || it.scope != DependencyScope.TEST }
        .mapNotNull {
          it.module.resolve(storage)
        }
        .mapNotNull { moduleMap.getDataByEntity(it) }
        .iterator()
    }
  }))

  return@addMeasuredTime moduleGraph
}

private fun modules(storage: EntityStorage): Sequence<ModuleBridge> = getModulesTimeMs.addMeasuredTime {
  val moduleMap = storage.moduleMap
  return@addMeasuredTime storage.entities(ModuleEntity::class.java).mapNotNull { moduleMap.getDataByEntity(it) }
}

@Suppress("DuplicatedCode")
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
      loadAllModulesTimeCounter.record(loadAllModulesTimeMs.asMilliseconds())
      newModuleTimeCounter.record(newModuleTimeMs.asMilliseconds())
      newNonPersistentModuleTimeCounter.record(newNonPersistentModuleTimeMs.asMilliseconds())
      loadModuleTimeCounter.record(loadModuleTimeMs.asMilliseconds())
      setUnloadedModulesTimeCounter.record(setUnloadedModulesTimeMs.asMilliseconds())
      createModuleInstanceTimeCounter.record(createModuleInstanceTimeMs.asMilliseconds())
      buildModuleGraphTimeCounter.record(buildModuleGraphTimeMs.asMilliseconds())
      getModulesTimeCounter.record(getModulesTimeMs.asMilliseconds())
    },
    loadAllModulesTimeCounter, newModuleTimeCounter, newNonPersistentModuleTimeCounter, loadModuleTimeCounter,
    setUnloadedModulesTimeCounter, createModuleInstanceTimeCounter, buildModuleGraphTimeCounter, getModulesTimeCounter
  )
}

private fun EntityStorage.toSnapshot(): ImmutableEntityStorage {
  return when (this) {
    is ImmutableEntityStorage -> this
    is MutableEntityStorage -> this.toSnapshot()
    else -> error("Unexpected storage: $this")
  }
}
