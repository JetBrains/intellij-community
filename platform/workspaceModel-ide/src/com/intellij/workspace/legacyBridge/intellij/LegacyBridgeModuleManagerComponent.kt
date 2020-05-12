// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.intellij

import com.intellij.ProjectTopics
import com.intellij.concurrency.JobSchedulerImpl
import com.intellij.configurationStore.ModuleStoreBase
import com.intellij.configurationStore.saveComponentManager
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.module.*
import com.intellij.openapi.module.impl.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.ProjectServiceContainerInitializedListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.graph.*
import com.intellij.workspace.api.*
import com.intellij.workspace.bracket
import com.intellij.workspace.executeOrQueueOnDispatchThread
import com.intellij.workspace.ide.*
import com.intellij.workspace.jps.JpsProjectEntitiesLoader
import com.intellij.workspace.legacyBridge.facet.FacetEntityChangeListener
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.util.*
import java.util.concurrent.Callable

@Suppress("ComponentNotRegistered")
class LegacyBridgeModuleManagerComponent(private val project: Project) : ModuleManagerEx(), Disposable {
  val outOfTreeModulesPath: String =
    FileUtilRt.toSystemIndependentName(File(PathManager.getTempPath(), "outOfTreeProjectModules-${project.locationHash}").path)
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)

  private val LOG = Logger.getInstance(javaClass)

  private val idToModule = Collections.synchronizedMap(LinkedHashMap<ModuleId, LegacyBridgeModule>())
  internal val unloadedModules: MutableMap<String, UnloadedModuleDescriptionImpl> = mutableMapOf()
  private val uncommittedModules = mutableMapOf<String, LegacyBridgeModule>()

  override fun dispose() {
    val modules = idToModule.values.toList()
    idToModule.clear()
    uncommittedModules.clear()

    for (module in modules) {
      Disposer.dispose(module)
    }
  }

  internal class MyProjectServiceContainerInitializedListener : ProjectServiceContainerInitializedListener {
    override fun serviceCreated(project: Project) {
      val activity = StartUpMeasurer.startMainActivity("(wm) module loading")
      val manager = ModuleManagerComponent.getInstance(project) as? LegacyBridgeModuleManagerComponent ?: return

      val unloadedNames = UnloadedModulesListStorage.getInstance(project).unloadedModuleNames.toSet()
      val entities = manager.entityStore.current.entities(ModuleEntity::class.java)
        .filter { !unloadedNames.contains(it.name) }
        .toList()
      manager.loadModules(entities)
      activity.end()
      activity.setDescription("(wm) module count: ${manager.modules.size}")
      WorkspaceModelTopics.getInstance(project).notifyModulesAreLoaded()
    }
  }

  init {
    // default project doesn't have modules
    if (!project.isDefault) {
      val busConnection = project.messageBus.connect(this)
      busConnection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
        override fun projectOpened(eventProject: Project) {
          val activity = StartUpMeasurer.startActivity("(wm) ProjectManagerListener.projectOpened")
          if (project == eventProject) {
            fireModulesAdded()
            for (module in idToModule.values) {
              (module as ModuleEx).projectOpened()
            }
          }
          activity.end()
        }

        override fun projectClosed(eventProject: Project) {
          if (project == eventProject) {
            for (module in idToModule.values) {
              (module as ModuleEx).projectClosed()
            }
          }
        }
      })

      WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(busConnection, object: WorkspaceModelChangeListener {
        override fun beforeChanged(event: EntityStoreChanged) = LOG.bracket("ModuleManagerComponent.BeforeEntityStoreChange") {
          for (change in event.getAllChanges()) {
            @Suppress("UNCHECKED_CAST")
            when (change.entity()) {
              is FacetEntity -> FacetEntityChangeListener.getInstance(project).processBeforeChange(change as EntityChange<FacetEntity>)
            }
          }
        }

        override fun changed(event: EntityStoreChanged) = LOG.bracket("ModuleManagerComponent.EntityStoreChange") {

          val moduleLibraryChanges = event.getChanges(LibraryEntity::class.java).filterModuleLibraryChanges()
          val changes = event.getChanges(ModuleEntity::class.java)
          val facetChanges = event.getChanges(FacetEntity::class.java)
          if (changes.isEmpty() && moduleLibraryChanges.isEmpty() && facetChanges.isEmpty()) return@bracket

          executeOrQueueOnDispatchThread {
            incModificationCount()

            val modulesToCheck = mutableSetOf<Module>()

            val unloadedModulesSetOriginal = unloadedModules.keys.toList()
            val unloadedModulesSet = unloadedModulesSetOriginal.toMutableSet()
            val oldModuleNames = mutableMapOf<Module, String>()

            for (change in moduleLibraryChanges) when (change) {
              is EntityChange.Removed -> processModuleLibraryChange(change, modulesToCheck)
              is EntityChange.Replaced -> processModuleLibraryChange(change, modulesToCheck)
              is EntityChange.Added -> Unit
            }

            for (change in facetChanges) when (change) {
              is EntityChange.Removed -> FacetEntityChangeListener.getInstance(project).processChange(change)
              is EntityChange.Replaced -> FacetEntityChangeListener.getInstance(project).processChange(change)
              is EntityChange.Added -> Unit
            }

            for (change in changes) processModuleChange(change, unloadedModulesSet, oldModuleNames)

            for (change in moduleLibraryChanges) when (change) {
              is EntityChange.Removed -> Unit
              is EntityChange.Replaced -> Unit
              is EntityChange.Added -> processModuleLibraryChange(change, modulesToCheck)
            }

            for (change in facetChanges) when (change) {
              is EntityChange.Removed -> Unit
              is EntityChange.Replaced -> Unit
              is EntityChange.Added -> FacetEntityChangeListener.getInstance(project).processChange(change)
            }

            // After every change processed
            postProcessModules(modulesToCheck, oldModuleNames, unloadedModulesSet)

            incModificationCount()
          }
        }
      })

      WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(busConnection, LegacyBridgeProjectRootsChangeListener(project))
    }
  }

  private fun postProcessModules(modulesToCheck: MutableSet<Module>,
                                 oldModuleNames: MutableMap<Module, String>,
                                 unloadedModulesSet: MutableSet<String>) {
    for (module in modulesToCheck) {
      val newModuleLibraries = LegacyBridgeModuleRootComponent.getInstance(module).newModuleLibraries
      if (newModuleLibraries.isNotEmpty()) {
        LOG.error("Not all module library instances were handled in change event. Leftovers:\n" +
                  newModuleLibraries.joinToString(separator = "\n"))
        newModuleLibraries.clear()
      }
    }

    if (oldModuleNames.isNotEmpty()) {
      project.messageBus
        .syncPublisher(ProjectTopics.MODULES)
        .modulesRenamed(project, oldModuleNames.keys.toList()) { module -> oldModuleNames[module] }
    }

    if (unloadedModulesSet.isNotEmpty()) {
      val loadedModules = modules.map { it.name }.toMutableList()
      loadedModules.removeAll(unloadedModulesSet)
      AutomaticModuleUnloader.getInstance(project).setLoadedModules(loadedModules)
    }
  }

  private fun processModuleChange(change: EntityChange<ModuleEntity>,
                                  unloadedModulesSet: MutableSet<String>,
                                  oldModuleNames: MutableMap<Module, String>) {
    when (change) {
      is EntityChange.Removed -> {
        // It's possible case then idToModule doesn't contain element e.g if unloaded module was removed
        idToModule[change.entity.persistentId()]?.let {
          fireBeforeModuleRemoved(change.entity.persistentId())
          removeModuleAndFireEvent(change.entity.persistentId())
        }
        unloadedModulesSet.remove(change.entity.name)
      }

      is EntityChange.Added -> {
        val moduleId = change.entity.persistentId()
        val alreadyCreatedModule = uncommittedModules.remove(moduleId.name)
        val module = if (alreadyCreatedModule != null) {
          unloadedModulesSet.remove(change.entity.name)
          unloadedModules.remove(change.entity.name)

          (alreadyCreatedModule as LegacyBridgeModuleImpl).entityStore = entityStore
          alreadyCreatedModule.diff = null
          if (WorkspaceModelTopics.getInstance(project).modulesAreLoaded) addModule(alreadyCreatedModule)
          alreadyCreatedModule
        }
        else {
          if (change.entity.name in unloadedModules.keys) {
            // Skip unloaded modules if it was not added via API
            return
          }

          if (WorkspaceModelTopics.getInstance(project).modulesAreLoaded) addModule(change.entity)
          else return
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
          unloadedModules.remove(change.newEntity.name)
          renameModule(oldId, newId)
          oldModuleNames[idToModule.getValue(newId)] = oldId.name
        }
      }
    }
  }

  private fun processModuleLibraryChange(change: EntityChange<LibraryEntity>, modulesToCheck: MutableSet<Module>) {
    when (change) {
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

          val moduleRootComponent = getModuleRootComponentByLibrary(change.oldEntity)
          val moduleLibrary = moduleRootComponent.moduleLibraries.firstOrNull { it.entityId == idBefore }
                              ?: error("Could not find library '${idBefore.name}' in module ${moduleRootComponent.module.name}")
          moduleLibrary.entityId = idAfter
        }

        Unit
      }
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
    }.let {} // exhaustive when
  }

  private fun getModuleRootComponentByLibrary(entity: LibraryEntity): LegacyBridgeModuleRootComponent {
    val tableId = entity.tableId as LibraryTableId.ModuleLibraryTableId
    val module = idToModule[tableId.moduleId] ?: error("Could not find module for module library: ${entity.persistentId()}")
    return LegacyBridgeModuleRootComponent.getInstance(module)
  }

  internal fun addModule(moduleEntity: ModuleEntity): LegacyBridgeModule {
    if (idToModule.containsKey(moduleEntity.persistentId())) {
      error("Module ${moduleEntity.name} (id:'${moduleEntity.persistentId()}') is already added")
    }

    val module = createModuleInstance(moduleEntity, entityStore, diff = null, isNew = false)
    addModule(module)
    return module
  }

  internal fun addModule(module: LegacyBridgeModule) {
    val oldValue = idToModule.put(module.moduleEntityId, module)
    if (oldValue != null) {
      LOG.warn("Duplicate module name: ${module.name}")
    }
  }

  internal fun removeModuleAndFireEvent(moduleEntityId: ModuleId) {
    val moduleImpl = idToModule.remove(moduleEntityId) ?: error("Module $moduleEntityId does not exist")
    project.messageBus.syncPublisher(ProjectTopics.MODULES).moduleRemoved(project, moduleImpl)
    Disposer.dispose(moduleImpl)
  }

  internal fun fireBeforeModuleRemoved(moduleEntityId: ModuleId) {
    val moduleImpl = idToModule[moduleEntityId] ?: error("Module $moduleEntityId does not exist")
    project.messageBus.syncPublisher(ProjectTopics.MODULES).beforeModuleRemoved(project, moduleImpl)
  }

  internal fun renameModule(oldId: ModuleId, newId: ModuleId) {
    val moduleImpl = idToModule.remove(oldId) ?: error("Module $oldId does not exist")

    val replacedModuleImplById = idToModule.put(newId, moduleImpl)
    if (replacedModuleImplById != null) {
      error("ModuleId $newId already exists")
    }

    // TODO Set notifyStorage to `true` after fixing module storages
    moduleImpl.rename(newId.name, true)
  }

  override fun moduleDependencyComparator(): Comparator<Module> {
    val builder = DFSTBuilder(moduleGraph(true))
    return builder.comparator()
  }

  override fun moduleGraph(): Graph<Module> = moduleGraph(includeTests = true)
  override fun moduleGraph(includeTests: Boolean): Graph<Module> {
    return GraphGenerator.generate(CachingSemiGraph.cache(object : InboundSemiGraph<Module> {
      override fun getNodes(): Collection<Module> = this@LegacyBridgeModuleManagerComponent.idToModule.values.toMutableList()

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

  private fun fireModulesAdded() {
    for (module in idToModule.values) {
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
    LegacyBridgeModifiableModuleModel(project, this, TypedEntityStorageBuilder.from(entityStore.current))

  fun getModifiableModel(diff: TypedEntityStorageBuilder): ModifiableModuleModel =
    LegacyBridgeModifiableModuleModel(project, this, diff)

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

  override fun getAllModuleDescriptions(): MutableCollection<ModuleDescription> =
    (idToModule.values.map { module ->
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
      JpsProjectEntitiesLoader.loadModule(moduleFile, project.configLocation!!, builder, virtualFileManager)
    }

    return findModuleByName(moduleName)
           ?: error("Module '$moduleName' was not found after loading: $filePath")
  }

  override fun getUnloadedModuleDescription(moduleName: String): UnloadedModuleDescription? = unloadedModules[moduleName]

  override fun getModules(): Array<Module> = idToModule.values.toTypedArray()

  private val sortedModulesValue = CachedValueWithParameter<Set<ModuleId>, Array<Module>> { _, _ ->
    val allModules = idToModule.values.toTypedArray<Module>()
    Arrays.sort(allModules, moduleDependencyComparator())
    return@CachedValueWithParameter allModules
  }

  override fun getSortedModules(): Array<Module> = entityStore.cachedValue(sortedModulesValue, idToModule.keys.toSet())

  override fun findModuleByName(name: String): Module? = idToModule[ModuleId(name)]

  @ApiStatus.Internal
  internal fun findUncommittedModuleByName(name: String): Module? = uncommittedModules[name]

  @ApiStatus.Internal
  internal fun addUncommittedModule(module: LegacyBridgeModule) {
    uncommittedModules[module.name] = module
  }

  @ApiStatus.Internal
  internal fun removeUncommittedModule(name: String) {
    uncommittedModules.remove(name)
  }

  override fun disposeModule(module: Module) = ApplicationManager.getApplication().runWriteAction {
    val modifiableModel = modifiableModel
    modifiableModel.disposeModule(module)
    modifiableModel.commit()
  }

  override fun setUnloadedModules(unloadedModuleNames: List<String>) {
    if (unloadedModules.keys == unloadedModuleNames.toSet()) {
      //optimization
      return
    }

    UnloadedModulesListStorage.getInstance(project).unloadedModuleNames = unloadedModuleNames

    if (unloadedModuleNames.isNotEmpty()) {
      val loadedModules = modules.map { it.name }.toMutableList()
      loadedModules.removeAll(unloadedModuleNames)
      AutomaticModuleUnloader.getInstance(project).setLoadedModules(loadedModules)
    }

    val names = unloadedModuleNames.toSet()

    val modulesToUnload = mutableSetOf<ModuleId>()
    for (module in modules) {
      if (module.name in names) {
        modulesToUnload.add((module as LegacyBridgeModule).moduleEntityId)
      }
    }

    val moduleEntities = entityStore.current.entities(ModuleEntity::class.java).toList()
    val moduleEntitiesToLoad = moduleEntities.filter { findModuleByName(it.name) == null && it.name !in names }

    unloadedModules.keys.removeAll { it !in names }
    runWriteAction {
      if (modulesToUnload.isNotEmpty()) {
        // we need to save module configurations before unloading, otherwise their settings will be lost
        saveComponentManager(project)
      }

      ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(
        {
          for (moduleId in modulesToUnload) {
            fireBeforeModuleRemoved(moduleId)

            val module = findModuleByName(moduleId.name)
            if (module != null) {
              val description = LoadedModuleDescriptionImpl(module)
              val modulePath = getModulePath(module, entityStore)
              val pointerManager = VirtualFilePointerManager.getInstance()
              val contentRoots = ModuleRootManager.getInstance(module).contentRootUrls.map {
                url -> pointerManager.create(url, this, null)
              }
              val unloadedModuleDescription = UnloadedModuleDescriptionImpl(modulePath, description.dependencyModuleNames, contentRoots)
              unloadedModules[moduleId.name] = unloadedModuleDescription
            }

            removeModuleAndFireEvent(moduleId)
          }

          loadModules(moduleEntitiesToLoad)
        }, false, true)
    }
  }

  override fun removeUnloadedModules(unloadedModules: MutableCollection<out UnloadedModuleDescription>) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    unloadedModules.forEach { this.unloadedModules.remove(it.name) }

    UnloadedModulesListStorage.getInstance(project).unloadedModuleNames = this.unloadedModules.keys.toList()
  }

  internal fun getModuleFilePath(moduleEntity: ModuleEntity): String {
    val jpsFileEntitySource = moduleEntity.entitySource as? JpsFileEntitySource.FileInDirectory

    // TODO Is this fallback fake path ok?
    val directoryPath = jpsFileEntitySource?.directory?.filePath ?: outOfTreeModulesPath
    return "$directoryPath/${moduleEntity.name}.iml"
  }

  fun createModuleInstance(moduleEntity: ModuleEntity,
                           entityStore: TypedEntityStore,
                           diff: TypedEntityStorageDiffBuilder?,
                           isNew: Boolean): LegacyBridgeModule {
    val modulePath = getModuleFilePath(moduleEntity)
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
        LocalFileSystem.getInstance().refreshAndFindFileByPath(modulePath)
        moduleStore.setPath(modulePath, null, isNew)
        moduleStore.storageManager.addMacro("MODULE_FILE", modulePath)
      }
      catch (t: Throwable) {
        logger<LegacyBridgeModuleManagerComponent>().error(t)
      }
    }

    return module
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): LegacyBridgeModuleManagerComponent {
      return ModuleManagerComponent.getInstance(project) as LegacyBridgeModuleManagerComponent
    }

    private fun EntityChange<LibraryEntity>.isModuleLibrary(): Boolean = when (this) {
      is EntityChange.Added -> entity.tableId is LibraryTableId.ModuleLibraryTableId
      is EntityChange.Removed -> entity.tableId is LibraryTableId.ModuleLibraryTableId
      is EntityChange.Replaced -> oldEntity.tableId is LibraryTableId.ModuleLibraryTableId
    }

    private fun List<EntityChange<LibraryEntity>>.filterModuleLibraryChanges() =
      filter {
        when (it) {
          is EntityChange.Added -> it.entity.tableId is LibraryTableId.ModuleLibraryTableId
          is EntityChange.Removed -> it.entity.tableId is LibraryTableId.ModuleLibraryTableId
          is EntityChange.Replaced -> it.oldEntity.tableId is LibraryTableId.ModuleLibraryTableId
        }
      }

    private fun EntityChange<*>.entity(): TypedEntity = when (this) {
      is EntityChange.Added -> entity
      is EntityChange.Removed -> entity
      is EntityChange.Replaced -> oldEntity
    }

    internal fun getModuleGroupPath(module: Module, entityStore: TypedEntityStore): Array<String>? {
      val moduleId = (module as LegacyBridgeModule).moduleEntityId

      val storage = entityStore.current
      val moduleEntity = storage.resolve(moduleId) ?: return null

      return moduleEntity.groupPath?.path?.toTypedArray()
    }

    internal fun getModulePath(module: Module, entityStore: TypedEntityStore): ModulePath = ModulePath(
      path = module.moduleFilePath,
      group = getModuleGroupPath(module, entityStore)?.joinToString(separator = ModuleManagerImpl.MODULE_GROUP_SEPARATOR)
    )

    internal fun hasModuleGroups(entityStore: TypedEntityStore) =
      entityStore.current.entities(ModuleGroupPathEntity::class.java).firstOrNull() != null
  }
}