// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.ProjectTopics
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.ModuleStore
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.*
import com.intellij.openapi.module.impl.*
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.serviceContainer.precomputeExtensionModel
import com.intellij.util.graph.*
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import kotlinx.coroutines.*
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import java.nio.file.Path
import java.util.*

@Suppress("OVERRIDE_DEPRECATION")
@ApiStatus.Internal
abstract class ModuleManagerBridgeImpl(private val project: Project) : ModuleManagerEx(), Disposable {
  protected val unloadedModules: MutableMap<String, UnloadedModuleDescription> = LinkedHashMap()

  override fun dispose() {
    modules().forEach(Disposer::dispose)
  }

  protected fun modules(): Sequence<ModuleBridge> {
    return modules(entityStore.current)
  }

  override fun areModulesLoaded(): Boolean {
    return WorkspaceModelTopics.getInstance(project).modulesAreLoaded
  }

  protected fun fireEventAndDisposeModule(module: ModuleBridge) {
    project.messageBus.syncPublisher(ProjectTopics.MODULES).moduleRemoved(project, module)
    Disposer.dispose(module)
  }

  protected fun fireBeforeModuleRemoved(module: ModuleBridge) {
    project.messageBus.syncPublisher(ProjectTopics.MODULES).beforeModuleRemoved(project, module)
  }

  override fun moduleDependencyComparator(): Comparator<Module> {
    return entityStore.cachedValue(dependencyComparatorValue)
  }

  override fun moduleGraph(): Graph<Module> = entityStore.cachedValue(dependencyGraphWithTestsValue)

  override fun moduleGraph(includeTests: Boolean): Graph<Module> {
    return entityStore.cachedValue(if (includeTests) dependencyGraphWithTestsValue else dependencyGraphWithoutTestsValue)
  }

  val entityStore = WorkspaceModel.getInstance(project).entityStorage

  suspend fun loadModules(entities: Sequence<ModuleEntity>, targetBuilder: MutableEntityStorage?, initializeFacets: Boolean) {
    val plugins = PluginManagerCore.getPluginSet().getEnabledModules()
    val corePlugin = plugins.firstOrNull { it.pluginId == PluginManagerCore.CORE_ID }
    val result = coroutineScope {
      val precomputedExtensionModel = precomputeExtensionModel()

      val unloadedModuleNames = UnloadedModulesListStorage.getInstance(project).unloadedModuleNames
      val (unloadedEntities, loadedEntities) = entities.partition { it.name in unloadedModuleNames }
      LOG.debug { "Loading modules for ${loadedEntities.size} entities" }

      val result = loadedEntities.map { moduleEntity ->
        async {
          try {
            val module = createModuleInstanceWithoutCreatingComponents(moduleEntity = moduleEntity,
                                                                       versionedStorage = entityStore,
                                                                       diff = targetBuilder,
                                                                       isNew = false,
                                                                       precomputedExtensionModel = precomputedExtensionModel,
                                                                       plugins = plugins,
                                                                       corePlugin = corePlugin)
            module.callCreateComponentsNonBlocking()
            moduleEntity to module
          }
          catch (e: CancellationException) {
            throw e
          }
          catch (e: Throwable) {
            LOG.error(e)
            null
          }
        }
      }

      UnloadedModuleDescriptionBridge.createDescriptions(unloadedEntities).associateByTo(unloadedModules) { it.name }

      result
    }.awaitAll()

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
    } else {
      fillBuilder(targetBuilder)
    }
    // Facets that are loaded from the cache do not generate "EntityAdded" event and aren't initialized
    // We initialize the facets manually here (after modules loading).
    if (initializeFacets) {
      blockingContext {
        invokeLater {
          for (module in modules) {
            if (!module.isDisposed) {
              module.initFacets()
            }
          }
        }
      }
    }
  }

  override fun unloadNewlyAddedModulesIfPossible(storage: EntityStorage) {
    val currentModuleNames = storage.entities(ModuleEntity::class.java).mapTo(HashSet()) { it.name }
    AutomaticModuleUnloader.getInstance(project).processNewModules(currentModuleNames, storage)
  }

  override fun getModifiableModel(): ModifiableModuleModel {
    return ModifiableModuleModelBridgeImpl(project, this, MutableEntityStorage.from(entityStore.current))
  }

  fun getModifiableModel(diff: MutableEntityStorage): ModifiableModuleModel {
    return ModifiableModuleModelBridgeImpl(project, this, diff, false)
  }

  override fun newModule(filePath: String, moduleTypeId: String): Module {
    incModificationCount()
    val modifiableModel = getModifiableModel()
    val module = modifiableModel.newModule(filePath, moduleTypeId)
    modifiableModel.commit()
    return module
  }

  override fun newNonPersistentModule(moduleName: String, id: String): Module {
    incModificationCount()
    val modifiableModel = getModifiableModel()
    val module = modifiableModel.newNonPersistentModule(moduleName, id)
    modifiableModel.commit()
    return module
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

  override fun loadModule(file: Path): Module {
    val model = getModifiableModel()
    val module = model.loadModule(file)
    model.commit()
    return module
  }

  override fun loadModule(filePath: String): Module {
    val model = getModifiableModel()
    val module = model.loadModule(filePath)
    model.commit()
    return module
  }

  override fun getUnloadedModuleDescription(moduleName: String): UnloadedModuleDescription? = unloadedModules[moduleName]

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

  override fun findModuleByName(name: String): Module? {
    return entityStore.current.resolve(ModuleId(name))?.findModule(entityStore.current)
  }

  override fun disposeModule(module: Module) = ApplicationManager.getApplication().runWriteAction {
    val modifiableModel = getModifiableModel()
    modifiableModel.disposeModule(module)
    modifiableModel.commit()
  }

  override suspend fun setUnloadedModules(unloadedModuleNames: List<String>) {
    // optimization
    if (unloadedModules.keys == unloadedModuleNames) {
      return
    }

    UnloadedModulesListStorage.getInstance(project).setUnloadedModuleNames(unloadedModuleNames)

    val unloadedModuleNamesSet = unloadedModuleNames.toHashSet()
    val moduleMap = entityStore.current.moduleMap
    val modulesToUnload = entityStore.current.entities(ModuleEntity::class.java)
      .filter { it.name in unloadedModuleNamesSet }
      .mapNotNull { moduleEntity ->
        val module = moduleMap.getDataByEntity(moduleEntity)
        module?.let { Pair(moduleEntity, module) }
      }.toList()
    val moduleEntitiesToLoad = entityStore.current.entities(ModuleEntity::class.java)
      .filter { moduleMap.getDataByEntity(it) == null && it.name !in unloadedModuleNamesSet }
      .toList()

    if (unloadedModuleNames.isNotEmpty()) {
      val loadedModules = modules.asSequence().map { it.name }.filter { it !in unloadedModuleNames }.toMutableList()
      moduleEntitiesToLoad.mapTo(loadedModules) { it.name }
      AutomaticModuleUnloader.getInstance(project).setLoadedModules(loadedModules)
    }
    else {
      AutomaticModuleUnloader.getInstance(project).setLoadedModules(emptyList())
    }

    unloadedModules.keys.removeAll { it !in unloadedModuleNamesSet }

    // we need to save module configurations before unloading, otherwise their settings will be lost
    if (modulesToUnload.isNotEmpty()) {
      blockingContext {
        project.save()
      }
    }

    withContext(Dispatchers.EDT) {
      ApplicationManager.getApplication().runWriteAction {
        ProjectRootManagerEx.getInstanceEx(project).withRootsChange(RootsChangeRescanningInfo.NO_RESCAN_NEEDED).use {
          for ((moduleEntity, module) in modulesToUnload) {
            fireBeforeModuleRemoved(module)

            val description = LoadedModuleDescriptionImpl(module)
            val modulePath = getModulePath(module, entityStore)
            val pointerManager = VirtualFilePointerManager.getInstance()
            val contentRoots = ModuleRootManager.getInstance(
              module).contentRootUrls.map { url ->
              pointerManager.create(url, this@ModuleManagerBridgeImpl, null)
            }
            val unloadedModuleDescription = UnloadedModuleDescriptionImpl(
              modulePath, description.dependencyModuleNames, contentRoots)
            unloadedModules[module.name] = unloadedModuleDescription
            (WorkspaceModel.getInstance(project) as WorkspaceModelImpl).updateProjectModelSilent("Remove mapping of the unloaded module") {
              it.mutableModuleMap.removeMapping(moduleEntity)
            }
            fireEventAndDisposeModule(module)
          }

          // Remove Facet bridges to recreate them. String constant is taken from
          // com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.FACET_BRIDGE_MAPPING_ID
          (WorkspaceModel.getInstance(project) as WorkspaceModelImpl).updateProjectModelSilent("Remove facet mapping of the unloaded module") { builder ->
            // TODO:: Fix fo external entities associated with facets
            moduleEntitiesToLoad.flatMap { it.facets }.forEach {
              builder.getMutableExternalMapping<Any>(
                "intellij.facets.bridge").removeMapping(it)
            }
          }
          // todo why we load modules in a write action
          runBlocking {
            loadModules(moduleEntitiesToLoad.asSequence(), null, true)
          }
        }
      }
    }
  }

  override fun setUnloadedModulesSync(unloadedModuleNames: List<String>) {
    if (!ApplicationManager.getApplication().isDispatchThread) {
      return runBlocking(CoreProgressManager.getCurrentThreadProgressModality().asContextElement()) { setUnloadedModules(unloadedModuleNames) }
    }

    ProgressManager.getInstance().runProcessWithProgressSynchronously(Runnable {
      val modalityState = CoreProgressManager.getCurrentThreadProgressModality()
      runBlocking(modalityState.asContextElement()) {
        setUnloadedModules(unloadedModuleNames)
      }
    }, "", true, project)
  }

  override fun removeUnloadedModules(unloadedModules: Collection<UnloadedModuleDescription>) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    unloadedModules.forEach { this.unloadedModules.remove(it.name) }

    UnloadedModulesListStorage.getInstance(project).setUnloadedModuleNames(this.unloadedModules.keys)
  }

  protected fun getModuleVirtualFileUrl(moduleEntity: ModuleEntity): VirtualFileUrl? {
    return getImlFileDirectory(moduleEntity)?.append("${moduleEntity.name}.iml")
  }

  protected fun getImlFileDirectory(moduleEntity: ModuleEntity): VirtualFileUrl? {
    val entitySource = when (val moduleSource = moduleEntity.entitySource) {
      is JpsFileDependentEntitySource -> moduleSource.originalSource
      is CustomModuleEntitySource -> moduleSource.internalSource
      else -> moduleEntity.entitySource
    }
    if (entitySource !is JpsFileEntitySource.FileInDirectory) {
      return null
    }
    return entitySource.directory
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
      moduleStore.setPath(moduleFileUrl.toPath(), null, isNew)
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
  ): ModuleBridge {
    val module = createModuleInstanceWithoutCreatingComponents(moduleEntity = moduleEntity,
                                                               versionedStorage = versionedStorage,
                                                               diff = diff,
                                                               isNew = isNew,
                                                               precomputedExtensionModel = precomputedExtensionModel,
                                                               plugins = plugins,
                                                               corePlugin = corePlugin)
    module.callCreateComponents()
    return module
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

  companion object {
    private val LOG = logger<ModuleManagerBridgeImpl>()
    private const val MODULE_BRIDGE_MAPPING_ID = "intellij.modules.bridge"

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

    @JvmStatic
    @Deprecated("Use ModuleBridgeUtils#findModuleEntity instead")
    fun EntityStorage.findModuleEntity(module: ModuleBridge) = moduleMap.getFirstEntity(module) as ModuleEntity?


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

    @JvmStatic
    fun getPathsToModuleFiles(element: Element): Set<ModulePath> {
      val paths = LinkedHashSet<ModulePath>()
      val modules = element.getChild(JpsProjectLoader.MODULES_TAG)
      if (modules == null) return paths
      for (moduleElement in modules.getChildren(JpsProjectLoader.MODULE_TAG)) {
        val fileUrlValue = moduleElement.getAttributeValue(JpsProjectLoader.FILE_URL_ATTRIBUTE)
        val filepath = if (fileUrlValue == null) { // support for older formats
          moduleElement.getAttributeValue(JpsProjectLoader.FILE_PATH_ATTRIBUTE)
        }
        else {
          VirtualFileManager.extractPath(fileUrlValue)
        }
        paths.add(ModulePath(path = FileUtilRt.toSystemIndependentName(filepath!!),
                             group = moduleElement.getAttributeValue(JpsProjectLoader.GROUP_ATTRIBUTE)))
      }
      return paths
    }

    private fun buildModuleGraph(storage: EntityStorage, includeTests: Boolean): Graph<Module> {
      return GraphGenerator.generate(CachingSemiGraph.cache(object : InboundSemiGraph<Module> {
        override fun getNodes(): Collection<Module> {
          return modules(storage).toList()
        }

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
    }

    private fun modules(storage: EntityStorage): Sequence<ModuleBridge> {
      val moduleMap = storage.moduleMap
      return storage.entities(ModuleEntity::class.java).mapNotNull { moduleMap.getDataByEntity(it) }
    }
  }
}
