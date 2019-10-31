package com.intellij.workspace.legacyBridge.intellij

import com.intellij.ProjectTopics
import com.intellij.concurrency.JobSchedulerImpl
import com.intellij.configurationStore.ModuleStoreBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.module.*
import com.intellij.openapi.module.impl.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectLifecycleListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.graph.*
import com.intellij.workspace.api.*
import com.intellij.workspace.bracket
import com.intellij.workspace.executeOrQueueOnDispatchThread
import com.intellij.workspace.ide.*
import com.intellij.workspace.jps.JpsProjectEntitiesLoader
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Suppress("ComponentNotRegistered")
class LegacyBridgeModuleManagerComponent(private val project: Project) : ModuleManager(), ProjectComponent, Disposable {

  private val LOG = Logger.getInstance(javaClass)

  override fun dispose() {
    val modules = modulesMap.values.toList()
    modulesMap.clear()

    for (module in modules) {
      Disposer.dispose(module)
    }
  }

  private val modulesMap: ConcurrentMap<ModuleId, LegacyBridgeModule> = ConcurrentHashMap()
  private val unloadedModules = UnloadedModulesListStorage.getInstance(project)
  private val newModuleInstances = mutableMapOf<ModuleId, LegacyBridgeModule>()

  @ApiStatus.Internal
  internal fun setNewModuleInstances(addedInstances: List<LegacyBridgeModule>) {
    if (newModuleInstances.isNotEmpty()) error("newModuleInstances are not empty")
    for (instance in addedInstances) {
      newModuleInstances[instance.moduleEntityId] = instance
    }
  }

  private fun getModuleRootComponentByLibrary(entity: LibraryEntity): LegacyBridgeModuleRootComponent {
    val tableId = entity.tableId as LibraryTableId.ModuleLibraryTableId
    val module = modulesMap[tableId.moduleId] ?: error("Could not find module for module library: ${entity.persistentId()}")
    return LegacyBridgeModuleRootComponent.getInstance(module)
  }

  init {
    // default project doesn't have modules
    if (!project.isDefault) {
      val myMessageBusConnection = project.messageBus.connect(this)
      myMessageBusConnection.subscribe(ProjectLifecycleListener.TOPIC, object : ProjectLifecycleListener {
        override fun projectComponentsInitialized(listenedProject: Project) {
          if (project !== listenedProject) return

          val unloadedNames = unloadedModules.unloadedModuleNames.toSet()

          val entities = entityStore.current.entities(ModuleEntity::class)
            .filter { !unloadedNames.contains(it.name) }
            .toList()
          loadModules(entities)
        }
      })

      myMessageBusConnection.subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
        override fun changed(event: EntityStoreChanged) = LOG.bracket("ModuleManagerComponent.EntityStoreChange") {
          val moduleLibraryChanges = event.getChanges(LibraryEntity::class.java).filterModuleLibraryChanges()
          val changes = event.getChanges(ModuleEntity::class.java)
          if (changes.isEmpty() && moduleLibraryChanges.isEmpty()) return@bracket

          executeOrQueueOnDispatchThread {
            LOG.bracket("ModuleManagerComponent.handleModulesChange") {
              incModificationCount()

              for (change in moduleLibraryChanges) when (change) {
                is EntityChange.Removed -> {
                  val moduleRootComponent = getModuleRootComponentByLibrary(change.entity)
                  val persistentId = change.entity.persistentId()
                  val moduleLibrary = moduleRootComponent.moduleLibraries.firstOrNull { it.entityId == persistentId }
                                      ?: error("Could not find library '${change.entity.name}' in module ${moduleRootComponent.module.name}")
                  moduleRootComponent.moduleLibraries.remove(moduleLibrary)
                  Disposer.dispose(moduleLibrary)
                }
                is EntityChange.Replaced -> {
                  val idBefore = change.oldEntity.persistentId()
                  val idAfter = change.newEntity.persistentId()

                  if (idBefore != idAfter) {
                    if (idBefore.tableId != idAfter.tableId) {
                      throw UnsupportedOperationException(
                        "Changing library table id is not allowed by replace entity operation." +
                        "old library id: '$idBefore' new library id: '$idAfter'")
                    }

                    // Since we do not track names in moduleRootComponent.moduleLibraries
                    // it's nothing to do here
                  }

                  Unit
                }
                is EntityChange.Added -> Unit // Add events are handled after adding new modules
              }.let {  } // exhaustive when

              val unloadedModulesSetOriginal = unloadedModules.unloadedModuleNames.toSet()
              val unloadedModulesSet = unloadedModulesSetOriginal.toMutableSet()
              val oldModuleNames = mutableMapOf<Module, String>()

              nextChange@ for (change in changes) when (change) {
                is EntityChange.Removed -> {
                  fireBeforeModuleRemoved(change.entity.persistentId())
                  removeModuleAndFireEvent(change.entity.persistentId())
                  unloadedModulesSet.remove(change.entity.name)
                }

                is EntityChange.Added -> {
                  val moduleId = change.entity.persistentId()
                  val alreadyCreatedModule = newModuleInstances.remove(moduleId)
                  val module = if (alreadyCreatedModule != null) {
                    unloadedModulesSet.remove(change.entity.name)

                    (alreadyCreatedModule as LegacyBridgeModuleImpl).entityStore = entityStore
                    alreadyCreatedModule.diff = null
                    addModule(alreadyCreatedModule)
                    alreadyCreatedModule
                  } else {
                    if (unloadedModulesSet.contains(change.entity.name)) {
                      // Skip unloaded modules if it was not added via API
                      continue@nextChange
                    }

                    addModule(change.entity)
                  }

                  if (project.isOpen) {
                    fireModuleAddedInWriteAction(module)
                  }
                }

                is EntityChange.Replaced -> {
                  val oldId = change.oldEntity.persistentId()
                  val newId = change.newEntity.persistentId()

                  if (oldId != newId) {
                    unloadedModulesSet.remove(change.newEntity.name)
                    renameModule(oldId, newId)
                    oldModuleNames[modulesMap.getValue(newId)] = oldId.name
                  }
                }
              }

              val modulesToCheck = mutableSetOf<Module>()
              for (change in moduleLibraryChanges) when (change) {
                is EntityChange.Added -> {
                  val moduleRootComponent = getModuleRootComponentByLibrary(change.entity)
                  modulesToCheck.add(moduleRootComponent.module)

                  val addedLibraryId = change.entity.persistentId()
                  val alreadyCreatedLibrary = moduleRootComponent.newModuleLibraries.firstOrNull { it.entityId == addedLibraryId }
                  val libraryImpl = if (alreadyCreatedLibrary != null) {
                    moduleRootComponent.newModuleLibraries.remove(alreadyCreatedLibrary)
                    alreadyCreatedLibrary.entityStore = entityStore
                    alreadyCreatedLibrary.modifiableModelFactory = null
                    alreadyCreatedLibrary
                  }
                  else {
                    moduleRootComponent.createModuleLibrary(addedLibraryId)
                  }

                  moduleRootComponent.moduleLibraries.add(libraryImpl)

                  Unit
                }
                is EntityChange.Replaced -> Unit
                is EntityChange.Removed -> Unit
              }.let {  } // exhaustive when

              if (oldModuleNames.isNotEmpty()) {
                project.messageBus
                  .syncPublisher(ProjectTopics.MODULES)
                  .modulesRenamed(project, oldModuleNames.keys.toList()) { module -> oldModuleNames[module] }
              }

              if (unloadedModulesSetOriginal != unloadedModulesSet) {
                setUnloadedModules(unloadedModulesSet.toList())
              }

              for (module in modulesToCheck) {
                val newModuleLibraries = LegacyBridgeModuleRootComponent.getInstance(module).newModuleLibraries
                if (newModuleLibraries.isNotEmpty()) {
                  LOG.error("Not all module library instances were handled in change event. Leftovers:\n" +
                            newModuleLibraries.joinToString(separator = "\n"))
                  newModuleLibraries.clear()
                }
              }

              if (newModuleInstances.isNotEmpty()) {
                LOG.error("Not all module instances were handled in change event. Leftovers:\n" +
                          newModuleInstances.keys.joinToString(separator = "\n"))
                newModuleInstances.clear()
              }

              incModificationCount()
            }
          }
        }
      })
    }
  }

  internal fun addModule(moduleEntity: ModuleEntity): LegacyBridgeModule {
    if (modulesMap.containsKey(moduleEntity.persistentId())) {
      error("Module ${moduleEntity.name} (id:'${moduleEntity.persistentId()}') is already added")
    }

    val module = createModuleInstance(project, moduleEntity, entityStore, diff = null, isNew = false)
    addModule(module)
    return module
  }

  internal fun addModule(module: LegacyBridgeModule) {
    val oldValue = modulesMap.put(module.moduleEntityId, module)
    if (oldValue != null) {
      LOG.warn("Duplicate module name: ${module.name}")
    }
  }

  internal fun removeModuleAndFireEvent(moduleEntityId: ModuleId) {
    val moduleImpl = modulesMap.remove(moduleEntityId) ?: error("Module $moduleEntityId does not exist")
    project.messageBus.syncPublisher(ProjectTopics.MODULES).moduleRemoved(project, moduleImpl)
    Disposer.dispose(moduleImpl)
  }

  internal fun fireBeforeModuleRemoved(moduleEntityId: ModuleId) {
    val moduleImpl = modulesMap[moduleEntityId] ?: error("Module $moduleEntityId does not exist")
    project.messageBus.syncPublisher(ProjectTopics.MODULES).beforeModuleRemoved(project, moduleImpl)
  }

  internal fun renameModule(oldId: ModuleId, newId: ModuleId) {
    val moduleImpl = modulesMap.remove(oldId) ?: error("Module $oldId does not exist")

    val replacedModuleImplById = modulesMap.put(newId, moduleImpl)
    if (replacedModuleImplById != null) {
      error("ModuleId $newId already exists")
    }

    // TODO Set notifyStorage to `true` after fixing module storages
    moduleImpl.rename(newId.name, false)
  }

  override fun moduleDependencyComparator(): Comparator<Module> {
    val builder = DFSTBuilder(moduleGraph(true))
    return builder.comparator()
  }

  override fun moduleGraph(): Graph<Module> = moduleGraph(includeTests = true)
  override fun moduleGraph(includeTests: Boolean): Graph<Module> {
    return GraphGenerator.generate(CachingSemiGraph.cache(object : InboundSemiGraph<Module> {
      override fun getNodes(): Collection<Module> = this@LegacyBridgeModuleManagerComponent.modulesMap.values.toMutableList()

      override fun getIn(m: Module): Iterator<Module> {
        val dependentModules = ModuleRootManager.getInstance(m).getDependencies(includeTests)
        return dependentModules.toList().iterator()
      }
    }))
  }

  private val entityStore by lazy { WorkspaceModel.getInstance(project).entityStore }

  private fun loadModules(entities: List<ModuleEntity>) {
    val service = AppExecutorUtil.createBoundedApplicationPoolExecutor("ModuleManager Loader", JobSchedulerImpl.getCPUCoresCount())
    try {
      val tasks = entities
        .map { moduleEntity ->
          Callable {
            LOG.runAndLogException {
              addModule(moduleEntity)
            }
          }
        }

      service.invokeAll(tasks)
    }
    finally {
      service.shutdownNow()
    }
  }

  override fun projectOpened() {
    fireModulesAdded()

    for (module in modulesMap.values) {
      (module as ModuleEx).projectOpened()
    }
  }

  override fun projectClosed() {
    for (module in modulesMap.values) {
      (module as ModuleEx).projectClosed()
    }
  }

  private fun fireModulesAdded() {
    for (module in modulesMap.values) {
      fireModuleAddedInWriteAction(module as ModuleEx)
    }
  }

  private fun fireModuleAddedInWriteAction(module: ModuleEx) {
    ApplicationManager.getApplication().runWriteAction {
      if (!module.isLoaded) {
        module.moduleAdded()
        fireModuleAdded(module)
      }
    }
  }

  private fun fireModuleAdded(module: Module) {
    project.messageBus.syncPublisher(ProjectTopics.MODULES).moduleAdded(project, module)
  }

  override fun getModifiableModel(): ModifiableModuleModel =
    LegacyBridgeModifiableModuleModel(entityStore.current, project, this)

  override fun newModule(filePath: String, moduleTypeId: String): Module {
    incModificationCount()
    val modifiableModel = modifiableModel
    val module = modifiableModel.newModule(filePath, moduleTypeId)
    modifiableModel.commit()
    return module
  }

  override fun getModuleDependentModules(module: Module): MutableList<Module> =
    ModuleRootManager.getInstance(module).dependencies.toMutableList()

  override fun getUnloadedModuleDescriptions(): MutableCollection<UnloadedModuleDescription> {
    val names = unloadedModules.unloadedModuleNames.toSet()
    val entities = entityStore.current.entities(ModuleEntity::class).filter { names.contains(it.name) }.toList()
    return entities.map { getUnloadedModuleDescription(it) }.toMutableList()
  }

  override fun hasModuleGroups(): Boolean = hasModuleGroups(entityStore)

  override fun isModuleDependent(module: Module, onModule: Module): Boolean =
    ModuleRootManager.getInstance(module).isDependsOn(onModule)

  override fun getAllModuleDescriptions(): MutableCollection<ModuleDescription> =
    (modulesMap.values.map { module ->
      object : ModuleDescription {
        override fun getName(): String = module.name

        override fun getDependencyModuleNames(): MutableList<String> =
          this@LegacyBridgeModuleManagerComponent
            .getModuleDependentModules(module)
            .map { it.name }
            .toMutableList()
      }
    } + unloadedModuleDescriptions).toMutableList()

  override fun getModuleGroupPath(module: Module): Array<String>? = getModuleGroupPath(module, entityStore)

  override fun getModuleGrouper(model: ModifiableModuleModel?): ModuleGrouper = createGrouper(project, model)

  override fun loadModule(filePath: String): Module {
    val moduleName = getModuleNameByFilePath(filePath)
    if (findModuleByName(moduleName) != null) {
      error("Module name '$moduleName' already exists. Trying to load module: $filePath")
    }

    val moduleFile = File(filePath)

    WorkspaceModel.getInstance(project).updateProjectModel { builder ->
      JpsProjectEntitiesLoader.loadModule(moduleFile, project.storagePlace!!, builder)
    }

    return findModuleByName(moduleName)
           ?: error("Module '$moduleName' was not found after loading: $filePath")
  }

  override fun getUnloadedModuleDescription(moduleName: String): UnloadedModuleDescription? {
    // TODO Optimize?

    val moduleEntity = entityStore.current.entities(ModuleEntity::class).filter { it.name == moduleName }.firstOrNull()
                       ?: return null
    return getUnloadedModuleDescription(moduleEntity)
  }

  override fun getModules(): Array<Module> = modulesMap.values.toTypedArray()

  private val sortedModulesValue = CachedValueWithParameter<Set<ModuleId>, Array<Module>> { _, _ ->
    val allModules = modulesMap.values.toTypedArray<Module>()
    Arrays.sort(allModules, moduleDependencyComparator())
    return@CachedValueWithParameter allModules
  }

  override fun getSortedModules(): Array<Module> = entityStore.cachedValue(sortedModulesValue, modulesMap.keys.toSet())

  override fun findModuleByName(name: String): Module? = modulesMap[ModuleId(name)]

  override fun disposeModule(module: Module) = ApplicationManager.getApplication().runWriteAction {
    val modifiableModel = modifiableModel
    modifiableModel.disposeModule(module)
    modifiableModel.commit()
  }

  override fun setUnloadedModules(unloadedModuleNames: List<String>) {
    unloadedModules.unloadedModuleNames = unloadedModuleNames

    if (unloadedModuleNames.isNotEmpty()) {
      val loadedModules = modules.map { it.name }.toMutableList()
      loadedModules.removeAll(unloadedModuleNames)
      AutomaticModuleUnloader.getInstance(project).setLoadedModules(loadedModules)
    }

    val names = unloadedModuleNames.toSet()

    val modulesToUnload = mutableSetOf<ModuleId>()
    for (module in modules) {
      if (names.contains(module.name)) {
        modulesToUnload.add((module as LegacyBridgeModule).moduleEntityId)
      }
    }

    val moduleEntities = entityStore.current.entities(ModuleEntity::class).toList()
    val moduleEntitiesToLoad = moduleEntities.filter { findModuleByName(it.name) == null && !names.contains(it.name) }

    if (moduleEntitiesToLoad.isEmpty() && modulesToUnload.isEmpty()) return

    ApplicationManager.getApplication().runWriteAction {
      ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(
        {
          modulesToUnload.forEach { moduleId ->
            fireBeforeModuleRemoved(moduleId)

            // we need to save module configuration before unloading, otherwise its settings will be lost
            // copied from original ModuleManagerImpl
            // TODO Uncomment after implementing save
            // saveComponentManager(module)

            removeModuleAndFireEvent(moduleId)
          }

          loadModules(moduleEntitiesToLoad)
        }, false, true)
    }
  }

  override fun removeUnloadedModules(unloadedModules: MutableCollection<out UnloadedModuleDescription>) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val unloadedModulesSet = unloadedModules.map { it.name }.toSet()
    val newUnloadedModules = this.unloadedModules.unloadedModuleNames.filter { !unloadedModulesSet.contains(it) }

    setUnloadedModules(newUnloadedModules)
  }

  private fun getUnloadedModuleDescription(moduleEntity: ModuleEntity): UnloadedModuleDescription = object : UnloadedModuleDescription {
    override fun getGroupPath(): List<String> = moduleEntity.groupPath?.path?.toList() ?: emptyList()

    override fun getName(): String = moduleEntity.name

    override fun getDependencyModuleNames(): List<String> =
      moduleEntity.dependencies.filterIsInstance<ModuleDependencyItem.Exportable.ModuleDependency>().map { it.module.name }

    override fun getContentRoots(): List<VirtualFilePointer> {
      val provider = LegacyBridgeFilePointerProvider.getInstance(project)
      return moduleEntity.contentRoots.map { it.url }.map { provider.getAndCacheFilePointer(it) }.toList()
    }
  }

  companion object {
    private fun List<EntityChange<LibraryEntity>>.filterModuleLibraryChanges() =
      filter {
        when (it) {
          is EntityChange.Added -> it.entity.tableId is LibraryTableId.ModuleLibraryTableId
          is EntityChange.Removed -> it.entity.tableId is LibraryTableId.ModuleLibraryTableId
          is EntityChange.Replaced -> it.oldEntity.tableId is LibraryTableId.ModuleLibraryTableId
        }
      }

    internal fun getModuleGroupPath(module: Module, entityStore: TypedEntityStore): Array<String>? {
      val moduleId = (module as LegacyBridgeModule).moduleEntityId

      val storage = entityStore.current
      val moduleEntity = storage.resolve(moduleId) ?: return null

      return moduleEntity.groupPath?.path?.toTypedArray()
    }

    internal fun hasModuleGroups(entityStore: TypedEntityStore) =
      entityStore.current.entities(ModuleGroupPathEntity::class).firstOrNull() != null

    internal fun createModuleInstance(project: Project,
                                      moduleEntity: ModuleEntity,
                                      entityStore: TypedEntityStore,
                                      diff: TypedEntityStorageDiffBuilder?,
                                      isNew: Boolean): LegacyBridgeModule {

      val jpsFileEntitySource = moduleEntity.entitySource as? JpsFileEntitySource

      // TODO Is this fallback fake path ok?
      val modulePath = jpsFileEntitySource?.file?.filePath
                       ?: "/module-id-${moduleEntity.persistentId()}.iml".replace(Regex("[^a-zA-Z0-9\\-/.]"), "_")

      val module = LegacyBridgeModuleImpl(
        name = moduleEntity.name,
        project = project,
        filePath = modulePath,
        moduleEntityId = moduleEntity.persistentId(),
        entityStore = entityStore,
        diff = diff
      )

      module.init {
        try {
          val moduleStore = module.stateStore as ModuleStoreBase
          moduleStore.setPath(modulePath, isNew)
          moduleStore.storageManager.addMacro("MODULE_FILE", modulePath)
        }
        catch (t: Throwable) {
          logger<LegacyBridgeModuleManagerComponent>().error(t)
        }
      }

      return module
    }
  }
}
