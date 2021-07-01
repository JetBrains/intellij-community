// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.ProjectTopics
import com.intellij.configurationStore.saveComponentManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.ModuleStore
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.*
import com.intellij.openapi.module.impl.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.serviceContainer.precomputeExtensionModel
import com.intellij.util.graph.*
import com.intellij.util.io.systemIndependentPath
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinTask

@ApiStatus.Internal
abstract class ModuleManagerBridgeImpl(private val project: Project) : ModuleManagerEx(), Disposable {
  protected val unloadedModules: MutableMap<String, UnloadedModuleDescription> = LinkedHashMap()

  override fun dispose() {
    modules().forEach {
      Disposer.dispose(it)
    }
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

  fun loadModules(entities: Sequence<ModuleEntity>) {
    val unloadedModuleNames = UnloadedModulesListStorage.getInstance(project).unloadedModuleNames
    val (unloadedEntities, loadedEntities) = entities.partition { it.name in unloadedModuleNames }
    LOG.debug { "Loading modules for ${loadedEntities.size} entities" }

    val plugins = PluginManagerCore.getLoadedPlugins(null)
    val precomputedExtensionModel = precomputeExtensionModel(plugins)

    val tasks = loadedEntities.map { moduleEntity ->
      ForkJoinTask.adapt(Callable {
        runCatching {
          val module = createModuleInstance(moduleEntity, entityStore, null, false, precomputedExtensionModel)
          moduleEntity to module
        }.getOrLogException(LOG)
      })
    }

    ForkJoinTask.invokeAll(tasks)
    UnloadedModuleDescriptionBridge.createDescriptions(unloadedEntities).associateByTo(unloadedModules) { it.name }

    WorkspaceModel.getInstance(project).updateProjectModelSilent { builder ->
      val moduleMap = builder.mutableModuleMap
      for (task in tasks) {
        val (entity, module) = task.rawResult ?: continue
        moduleMap.addMapping(entity, module)
        (ModuleRootComponentBridge.getInstance(
          module).getModuleLibraryTable() as ModuleLibraryTableBridgeImpl).registerModuleLibraryInstances(builder)
      }
    }
  }

  override fun unloadNewlyAddedModulesIfPossible(storage: WorkspaceEntityStorage) {
    val currentModuleNames = storage.entities(ModuleEntity::class.java).mapTo(HashSet()) { it.name }
    AutomaticModuleUnloader.getInstance(project).processNewModules(currentModuleNames, storage)
  }

  override fun getModifiableModel(): ModifiableModuleModel =
    ModifiableModuleModelBridgeImpl(project, this, WorkspaceEntityStorageBuilder.from(entityStore.current))

  fun getModifiableModel(diff: WorkspaceEntityStorageBuilder): ModifiableModuleModel =
    ModifiableModuleModelBridgeImpl(project, this, diff, false)

  override fun newModule(filePath: String, moduleTypeId: String): Module {
    incModificationCount()
    val modifiableModel = modifiableModel
    val module = modifiableModel.newModule(filePath, moduleTypeId)
    modifiableModel.commit()
    return module
  }

  override fun newNonPersistentModule(moduleName: String, id: String): Module {
    incModificationCount()
    val modifiableModel = modifiableModel
    val module = modifiableModel.newNonPersistentModule(moduleName, id)
    modifiableModel.commit()
    return module
  }

  override fun getModuleDependentModules(module: Module): List<Module> = modules.filter { isModuleDependent(it, module) }

  override fun getUnloadedModuleDescriptions(): Collection<UnloadedModuleDescription> = unloadedModules.values

  override fun getFailedModulePaths(): Collection<ModulePath> = emptyList()

  override fun hasModuleGroups(): Boolean = hasModuleGroups(entityStore)

  override fun isModuleDependent(module: Module, onModule: Module): Boolean =
    ModuleRootManager.getInstance(module).isDependsOn(onModule)

  override fun getAllModuleDescriptions(): Collection<ModuleDescription> =
    (modules().map { LoadedModuleDescriptionImpl(it) } + unloadedModuleDescriptions).toList()

  override fun getModuleGroupPath(module: Module): Array<String>? = getModuleGroupPath(module, entityStore)

  override fun getModuleGrouper(model: ModifiableModuleModel?): ModuleGrouper = createGrouper(project, model)

  override fun loadModule(file: Path): Module {
    return loadModule(file.systemIndependentPath)
  }

  override fun loadModule(filePath: String): Module {
    val model = modifiableModel
    val module = model.loadModule(filePath)
    model.commit()
    return module
  }

  override fun getUnloadedModuleDescription(moduleName: String): UnloadedModuleDescription? = unloadedModules[moduleName]

  private val modulesArrayValue = CachedValue<Array<Module>> { storage ->
    modules(storage).toList().toTypedArray()
  }

  override fun getModules(): Array<Module> = entityStore.cachedValue(modulesArrayValue)

  private val sortedModulesValue = CachedValue { storage ->
    val allModules = modules(storage).toList().toTypedArray<Module>()
    Arrays.sort(allModules, moduleDependencyComparator())
    allModules
  }

  override fun getSortedModules(): Array<Module> = entityStore.cachedValue(sortedModulesValue)

  override fun findModuleByName(name: String): Module? {
    val entity = entityStore.current.resolve(ModuleId(name)) ?: return null
    return entityStore.current.findModuleByEntity(entity)
  }

  override fun disposeModule(module: Module) = ApplicationManager.getApplication().runWriteAction {
    val modifiableModel = modifiableModel
    modifiableModel.disposeModule(module)
    modifiableModel.commit()
  }

  override fun setUnloadedModules(unloadedModuleNames: List<String>) {
    if (unloadedModules.keys == unloadedModuleNames) {
      // optimization
      return
    }

    UnloadedModulesListStorage.getInstance(project).setUnloadedModuleNames(unloadedModuleNames)

    val unloadedModuleNamesSet = unloadedModuleNames.toSet()
    val moduleMap = entityStore.current.moduleMap
    val modulesToUnload = entityStore.current.entities(ModuleEntity::class.java)
      .filter { it.name in unloadedModuleNamesSet }
      .mapNotNull { moduleEntity ->
        val module = moduleMap.getDataByEntity(moduleEntity)
        module?.let { Pair(moduleEntity, module) }
      }
      .toList()
    val moduleEntitiesToLoad = entityStore.current.entities(ModuleEntity::class.java)
      .filter { moduleMap.getDataByEntity(it) == null && it.name !in unloadedModuleNamesSet }.toList()

    if (unloadedModuleNames.isNotEmpty()) {
      val loadedModules = modules.map { it.name }.toMutableList()
      loadedModules.removeAll(unloadedModuleNames)
      moduleEntitiesToLoad.mapTo(loadedModules) { it.name }
      AutomaticModuleUnloader.getInstance(project).setLoadedModules(loadedModules)
    }
    else {
      AutomaticModuleUnloader.getInstance(project).setLoadedModules(emptyList())
    }

    unloadedModules.keys.removeAll { it !in unloadedModuleNamesSet }
    runWriteAction {
      if (modulesToUnload.isNotEmpty()) {
        // we need to save module configurations before unloading, otherwise their settings will be lost
        saveComponentManager(project)
      }

      ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(
        {
          for ((moduleEntity, module) in modulesToUnload) {
            fireBeforeModuleRemoved(module)

            val description = LoadedModuleDescriptionImpl(module)
            val modulePath = getModulePath(module, entityStore)
            val pointerManager = VirtualFilePointerManager.getInstance()
            val contentRoots = ModuleRootManager.getInstance(module).contentRootUrls.map { url ->
              pointerManager.create(url, this, null)
            }
            val unloadedModuleDescription = UnloadedModuleDescriptionImpl(modulePath, description.dependencyModuleNames, contentRoots)
            unloadedModules[module.name] = unloadedModuleDescription
            WorkspaceModel.getInstance(project).updateProjectModelSilent {
              it.mutableModuleMap.removeMapping(moduleEntity)
            }
            fireEventAndDisposeModule(module)
          }

          loadModules(moduleEntitiesToLoad.asSequence())
        }, false, true)
    }
  }

  override fun removeUnloadedModules(unloadedModules: MutableCollection<out UnloadedModuleDescription>) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    unloadedModules.forEach { this.unloadedModules.remove(it.name) }

    UnloadedModulesListStorage.getInstance(project).setUnloadedModuleNames(this.unloadedModules.keys)
  }

  protected fun getModuleVirtualFileUrl(moduleEntity: ModuleEntity): VirtualFileUrl? {
    val entitySource = when (val moduleSource = moduleEntity.entitySource) {
      is JpsFileDependentEntitySource -> moduleSource.originalSource
      is CustomModuleEntitySource -> moduleSource.internalSource
      else -> moduleEntity.entitySource
    }
    if (entitySource !is JpsFileEntitySource.FileInDirectory) {
      return null
    }
    return entitySource.directory.append("${moduleEntity.name}.iml")
  }

  fun createModuleInstance(moduleEntity: ModuleEntity, versionedStorage: VersionedEntityStorage,
                                             diff: WorkspaceEntityStorageDiffBuilder?, isNew: Boolean,
                                             precomputedExtensionModel: PrecomputedExtensionModel?): ModuleBridge {
    val plugins = PluginManagerCore.getLoadedPlugins(null)
    val corePlugin = plugins.find { it.pluginId == PluginManagerCore.CORE_ID }
    val moduleFileUrl = getModuleVirtualFileUrl(moduleEntity)

    val module = createModule(persistentId = moduleEntity.persistentId(),
                              name = moduleEntity.name,
                              virtualFileUrl = moduleFileUrl,
                              entityStorage = versionedStorage,
                              diff = diff)

    module.registerComponents(corePlugin = corePlugin,
                              plugins = plugins,
                              app = ApplicationManager.getApplication(),
                              precomputedExtensionModel = precomputedExtensionModel,
                              listenerCallbacks = null)

    if (moduleFileUrl == null) {
      registerNonPersistentModuleStore(module)
    }
    else {
      val moduleStore = module.getService(IComponentStore::class.java) as ModuleStore
      moduleStore.setPath(moduleFileUrl.toPath(), null, isNew)
    }

    module.callCreateComponents()
    return module
  }

  open fun registerNonPersistentModuleStore(module: ModuleBridge) { }

  abstract fun loadModuleToBuilder(moduleName: String, filePath: String, diff: WorkspaceEntityStorageBuilder): ModuleEntity

  abstract fun createModule(persistentId: ModuleId, name: String, virtualFileUrl: VirtualFileUrl?, entityStorage: VersionedEntityStorage,
                            diff: WorkspaceEntityStorageDiffBuilder?): ModuleBridge

  companion object {
    private val LOG = logger<ModuleManagerBridgeImpl>()
    private const val MODULE_BRIDGE_MAPPING_ID = "intellij.modules.bridge"

    fun getInstance(project: Project): ModuleManagerBridgeImpl {
      return ModuleManager.getInstance(project) as ModuleManagerBridgeImpl
    }

    val WorkspaceEntityStorage.moduleMap: ExternalEntityMapping<ModuleBridge>
      get() = getExternalMapping(MODULE_BRIDGE_MAPPING_ID)
    val WorkspaceEntityStorageDiffBuilder.mutableModuleMap: MutableExternalEntityMapping<ModuleBridge>
      get() = getMutableExternalMapping(MODULE_BRIDGE_MAPPING_ID)

    fun WorkspaceEntityStorage.findModuleEntity(module: ModuleBridge) =
      moduleMap.getEntities(module).firstOrNull() as ModuleEntity?

    fun WorkspaceEntityStorage.findModuleByEntity(entity: ModuleEntity): ModuleBridge? = moduleMap.getDataByEntity(entity)

    internal fun getModuleGroupPath(module: Module, entityStorage: VersionedEntityStorage): Array<String>? {
      val moduleEntity = entityStorage.current.findModuleEntity(module as ModuleBridge) ?: return null
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
    fun changeModuleEntitySource(module: ModuleBridge, moduleEntityStore: WorkspaceEntityStorage, newSource: EntitySource,
                                 moduleDiff: WorkspaceEntityStorageDiffBuilder?) {
      val oldEntitySource = moduleEntityStore.findModuleEntity(module)?.entitySource ?: return
      fun changeSources(diffBuilder: WorkspaceEntityStorageDiffBuilder, storage: WorkspaceEntityStorage) {
        val entitiesMap = storage.entitiesBySource { it == oldEntitySource }
        entitiesMap.values.asSequence().flatMap { it.values.asSequence().flatten() }.forEach {
          if (it !is FacetEntity) {
            diffBuilder.changeSource(it, newSource)
          }
        }
      }

      if (moduleDiff != null) {
        changeSources(moduleDiff, moduleEntityStore)
      }
      else {
        WriteAction.runAndWait<RuntimeException> {
          WorkspaceModel.getInstance(module.project).updateProjectModel { builder ->
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
        val filepath = if (fileUrlValue == null) {
          // support for older formats
          moduleElement.getAttributeValue(JpsProjectLoader.FILE_PATH_ATTRIBUTE)
        }
        else {
          VirtualFileManager.extractPath(fileUrlValue)
        }
        paths.add(ModulePath(FileUtilRt.toSystemIndependentName(Objects.requireNonNull(filepath)),
                             moduleElement.getAttributeValue(JpsProjectLoader.GROUP_ATTRIBUTE)))
      }
      return paths
    }

    private fun buildModuleGraph(storage: WorkspaceEntityStorage, includeTests: Boolean): Graph<Module> {
      return GraphGenerator.generate(CachingSemiGraph.cache(object : InboundSemiGraph<Module> {
        override fun getNodes(): Collection<Module> {
          return modules(storage).toList()
        }

        override fun getIn(m: Module): Iterator<Module> {
          val moduleMap = storage.moduleMap
          val entity = moduleMap.getEntities(m as ModuleBridge).firstOrNull() as ModuleEntity?
          return (entity?.dependencies?.asSequence() ?: emptySequence())
            .filterIsInstance<ModuleDependencyItem.Exportable.ModuleDependency>()
            .filter { includeTests || it.scope != ModuleDependencyItem.DependencyScope.TEST }
            .mapNotNull { it.module.resolve(storage) }
            .mapNotNull { moduleMap.getDataByEntity(it) }
            .iterator()
        }
      }))
    }

    private fun modules(storage: WorkspaceEntityStorage): Sequence<ModuleBridge> {
      val moduleMap = storage.moduleMap
      return storage.entities(ModuleEntity::class.java).mapNotNull { moduleMap.getDataByEntity(it) }
    }
  }
}
