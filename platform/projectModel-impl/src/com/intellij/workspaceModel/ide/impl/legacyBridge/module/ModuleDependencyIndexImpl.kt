// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryNameGenerator
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyListener
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.*
import java.util.function.Supplier

class ModuleDependencyIndexImpl(private val project: Project): ModuleDependencyIndex, Disposable {
  companion object {
    private const val LIBRARY_NAME_DELIMITER = ":"
    @JvmStatic
    private val LOG = logger<ModuleDependencyIndexImpl>()
  }

  private val eventDispatcher = EventDispatcher.create(ModuleDependencyListener::class.java)
  
  private val libraryTablesListener = LibraryTablesListener()
  private val jdkChangeListener = JdkChangeListener()
  private val rootSetChangeListener = ReferencedRootSetChangeListener()
  
  init {
    if (!project.isDefault) {
      val messageBusConnection = project.messageBus.connect(this)
      messageBusConnection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, jdkChangeListener)
    }
  }

  override fun addListener(listener: ModuleDependencyListener) {
    eventDispatcher.addListener(listener)
  }

  override fun setupTrackedLibrariesAndJdks() {
    val currentStorage = WorkspaceModel.getInstance(project).entityStorage.current
    for (moduleEntity in currentStorage.entities(ModuleEntity::class.java)) {
      addTrackedLibraryAndJdkFromEntity(moduleEntity)
    }
  }

  override fun hasProjectSdkDependency(): Boolean {
    return jdkChangeListener.hasProjectSdkDependency()
  }

  override fun hasDependencyOn(libraryId: LibraryId): Boolean {
    return libraryId.tableId is LibraryTableId.ModuleLibraryTableId || libraryTablesListener.hasDependencyOn(libraryId)
  }

  override fun hasDependencyOn(library: Library): Boolean {
    return library.table == null || libraryTablesListener.hasDependencyOn(library)
  }

  override fun hasDependencyOn(sdk: Sdk): Boolean {
    return jdkChangeListener.hasDependencyOn(sdk)
  }

  private fun addTrackedLibraryAndJdkFromEntity(moduleEntity: ModuleEntity) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    LOG.debug { "Add tracked global libraries and JDK from ${moduleEntity.name}" }
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    moduleEntity.dependencies.forEach {
      when {
        it is ModuleDependencyItem.Exportable.LibraryDependency && it.library.tableId !is LibraryTableId.ModuleLibraryTableId -> {
          val libraryName = it.library.name
          val libraryLevel = it.library.tableId.level
          val libraryTable = libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project) ?: return@forEach
          if (libraryTablesListener.isEmpty(libraryLevel)) libraryTable.addListener(libraryTablesListener)
          libraryTablesListener.addTrackedLibrary(moduleEntity, libraryTable, libraryName)
        }
        it is ModuleDependencyItem.SdkDependency || it is ModuleDependencyItem.InheritedSdkDependency -> {
          jdkChangeListener.addTrackedJdk(it, moduleEntity)
        }
      }
    }
  }

  private fun removeTrackedLibrariesAndJdkFromEntity(moduleEntity: ModuleEntity) {
    LOG.debug { "Removed tracked global libraries and JDK from ${moduleEntity.name}" }
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    moduleEntity.dependencies.forEach {
      when {
        it is ModuleDependencyItem.Exportable.LibraryDependency && it.library.tableId !is LibraryTableId.ModuleLibraryTableId -> {
          val libraryName = it.library.name
          val libraryLevel = it.library.tableId.level
          val libraryTable = libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project) ?: return@forEach
          libraryTablesListener.unTrackLibrary(moduleEntity, libraryTable, libraryName)
          if (libraryTablesListener.isEmpty(libraryLevel)) libraryTable.removeListener(libraryTablesListener)
        }
        it is ModuleDependencyItem.SdkDependency || it is ModuleDependencyItem.InheritedSdkDependency -> {
          jdkChangeListener.removeTrackedJdk(it, moduleEntity)
        }
      }
    }
  }

  override fun dispose() {
    if (project.isDefault) return

    libraryTablesListener.unsubscribe()
    jdkChangeListener.unsubscribe()
  }

  private inner class ReferencedRootSetChangeListener : RootProvider.RootSetChangedListener {
    override fun rootSetChanged(wrapper: RootProvider) {
      if (wrapper is Library) {
        eventDispatcher.multicaster.referencedLibraryChanged(wrapper)
      }
      else {
        require(wrapper is Supplier<*>) { "Unexpected root provider $wrapper does not implement Supplier<Sdk>" }
        val value = wrapper.get()
        require(value is Sdk) { "Unexpected root provider $wrapper does not implement Supplier<Sdk>" }
        eventDispatcher.multicaster.referencedSdkChanged(value)
      }
    }
  }
  
  private inner class LibraryTablesListener : LibraryTable.Listener {
    private val librariesPerModuleMap = BidirectionalMultiMap<ModuleId, String>()

    fun addTrackedLibrary(moduleEntity: ModuleEntity, libraryTable: LibraryTable, libraryName: String) {
      val library = libraryTable.getLibraryByName(libraryName)
      val libraryIdentifier = getLibraryIdentifier(libraryTable, libraryName)
      if (!librariesPerModuleMap.containsValue(libraryIdentifier) && library != null) {
        library.rootProvider.addRootSetChangedListener(rootSetChangeListener)
        eventDispatcher.multicaster.addedDependencyOn(library)
      }
      librariesPerModuleMap.put(moduleEntity.symbolicId, libraryIdentifier)
    }

    fun unTrackLibrary(moduleEntity: ModuleEntity, libraryTable: LibraryTable, libraryName: String) {
      val library = libraryTable.getLibraryByName(libraryName)
      val libraryIdentifier = getLibraryIdentifier(libraryTable, libraryName)
      librariesPerModuleMap.remove(moduleEntity.symbolicId, libraryIdentifier)
      if (!librariesPerModuleMap.containsValue(libraryIdentifier) && library != null) {
        eventDispatcher.multicaster.removedDependencyOn(library)
        library.rootProvider.removeRootSetChangedListener(rootSetChangeListener)
      }
    }

    fun isEmpty(libraryLevel: String) = librariesPerModuleMap.values.none { it.startsWith("$libraryLevel$LIBRARY_NAME_DELIMITER") }

    fun getLibraryLevels() = librariesPerModuleMap.values.mapTo(HashSet()) { it.substringBefore(LIBRARY_NAME_DELIMITER) }

    override fun afterLibraryAdded(newLibrary: Library) {
      if (hasDependencyOn(newLibrary)) {
        eventDispatcher.multicaster.referencedLibraryAdded(newLibrary)
        eventDispatcher.multicaster.addedDependencyOn(newLibrary)
        newLibrary.rootProvider.addRootSetChangedListener(rootSetChangeListener)
      }
    }

    override fun afterLibraryRemoved(library: Library) {
      if (hasDependencyOn(library)) {
        library.rootProvider.removeRootSetChangedListener(rootSetChangeListener)
        eventDispatcher.multicaster.removedDependencyOn(library)
        eventDispatcher.multicaster.referencedLibraryRemoved(library)
      }
    }

    fun hasDependencyOn(library: Library) = librariesPerModuleMap.containsValue(getLibraryIdentifier(library))
    fun hasDependencyOn(libraryId: LibraryId) = librariesPerModuleMap.containsValue(getLibraryIdentifier(libraryId))

    override fun afterLibraryRenamed(library: Library, oldName: String?) {
      val libraryTable = library.table
      val newName = library.name
      if (libraryTable != null && oldName != null && newName != null) {
        val affectedModules = librariesPerModuleMap.getKeys(getLibraryIdentifier(libraryTable, oldName))
        if (affectedModules.isNotEmpty()) {
          val libraryTableId = LibraryNameGenerator.getLibraryTableId(libraryTable.tableLevel)
          WorkspaceModel.getInstance(project).updateProjectModel("Module dependency index: after library renamed") { builder ->
            //maybe it makes sense to simplify this code by reusing code from PEntityStorageBuilder.updateSoftReferences
            affectedModules.mapNotNull { builder.resolve(it) }.forEach { module ->
              val updated = module.dependencies.map {
                when {
                  it is ModuleDependencyItem.Exportable.LibraryDependency && it.library.tableId == libraryTableId && it.library.name == oldName ->
                    it.copy(library = LibraryId(newName, libraryTableId))
                  else -> it
                }
              } as MutableList<ModuleDependencyItem>
              builder.modifyEntity(module) {
                dependencies = updated
              }
            }
          }
        }
      }
    }

    private fun getLibraryIdentifier(library: Library) = "${library.table.tableLevel}$LIBRARY_NAME_DELIMITER${library.name}"
    private fun getLibraryIdentifier(libraryId: LibraryId) = "${libraryId.tableId.level}$LIBRARY_NAME_DELIMITER${libraryId.name}"
    private fun getLibraryIdentifier(libraryTable: LibraryTable,
                                     libraryName: String) = "${libraryTable.tableLevel}$LIBRARY_NAME_DELIMITER$libraryName"

    fun unsubscribe() {
      val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
      libraryTablesListener.getLibraryLevels().forEach { libraryLevel ->
        val libraryTable = libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project)
        libraryTable?.libraryIterator?.forEach {
          it.rootProvider.removeRootSetChangedListener(rootSetChangeListener)
        }
        libraryTable?.removeListener(libraryTablesListener)
      }
      librariesPerModuleMap.clear()
    }
  }

  private inner class JdkChangeListener : ProjectJdkTable.Listener, ProjectRootManagerEx.ProjectJdkListener {
    private val sdkDependencies = MultiMap.createSet<ModuleDependencyItem, ModuleId>()
    private val watchedSdks = HashSet<RootProvider>()
    private var watchedProjectSdk: Sdk? = null
    private var projectJdkListenerAdded = false

    override fun jdkAdded(jdk: Sdk) {
      if (hasDependencyOn(jdk)) {
        if (watchedSdks.isEmpty()) {
          eventDispatcher.multicaster.firstDependencyOnSdkAdded()
        }
        eventDispatcher.multicaster.referencedSdkAdded(jdk)
        if (hasProjectSdkDependency() && isProjectSdk(jdk)) {
          watchedProjectSdk = jdk
        }
        if (watchedSdks.add(jdk.rootProvider)) {
          eventDispatcher.multicaster.addedDependencyOn(jdk)
          jdk.rootProvider.addRootSetChangedListener(rootSetChangeListener)
        }
      }
    }

    override fun jdkNameChanged(jdk: Sdk, previousName: String) {
      val sdkDependency = ModuleDependencyItem.SdkDependency(previousName, jdk.sdkType.name)
      val affectedModules = sdkDependencies.get(sdkDependency)
      if (affectedModules.isNotEmpty()) {
        WorkspaceModel.getInstance(project).updateProjectModel("Module dependency index: jdk name changed") { builder ->
          for (moduleId in affectedModules) {
            val module = moduleId.resolve(builder) ?: continue
            val updated = module.dependencies.map {
              when (it) {
                is ModuleDependencyItem.SdkDependency -> ModuleDependencyItem.SdkDependency(jdk.name, jdk.sdkType.name)
                else -> it
              }
            } as MutableList<ModuleDependencyItem>
            builder.modifyEntity(module) {
              dependencies = updated
            }
          }
        }
      }
    }

    override fun jdkRemoved(jdk: Sdk) {
      if (hasProjectSdkDependency() && isProjectSdk(jdk)) {
        watchedProjectSdk = null
      }
      if (watchedSdks.remove(jdk.rootProvider)) {
        jdk.rootProvider.removeRootSetChangedListener(rootSetChangeListener)
        eventDispatcher.multicaster.removedDependencyOn(jdk)
      }
      if (hasDependencyOn(jdk)) {
        eventDispatcher.multicaster.referencedSdkRemoved(jdk)
      }
      if (watchedSdks.isEmpty()) {
        eventDispatcher.multicaster.lastDependencyOnSdkRemoved()
      }
    }

    fun addTrackedJdk(sdkDependency: ModuleDependencyItem, moduleEntity: ModuleEntity) {
      if (sdkDependency == ModuleDependencyItem.InheritedSdkDependency && !projectJdkListenerAdded) {
        (projectRootManager as ProjectRootManagerEx).addProjectJdkListener(this)
        projectJdkListenerAdded = true
      }
      val sdk = findSdk(sdkDependency)
      if (sdk != null) {
        if (sdkDependency == ModuleDependencyItem.InheritedSdkDependency) {
          watchedProjectSdk = sdk
        }
        addTrackedJdk(sdk)
      }
      sdkDependencies.putValue(sdkDependency, moduleEntity.symbolicId)
    }

    private fun addTrackedJdk(sdk: Sdk) {
      if (watchedSdks.isEmpty()) {
        eventDispatcher.multicaster.firstDependencyOnSdkAdded()
      }
      if (watchedSdks.add(sdk.rootProvider)) {
        eventDispatcher.multicaster.addedDependencyOn(sdk)
        sdk.rootProvider.addRootSetChangedListener(rootSetChangeListener)
      }
    }

    fun removeTrackedJdk(sdkDependency: ModuleDependencyItem, moduleEntity: ModuleEntity) {
      sdkDependencies.remove(sdkDependency, moduleEntity.symbolicId)
      val sdk = findSdk(sdkDependency)
      if (sdkDependency == ModuleDependencyItem.InheritedSdkDependency && !hasProjectSdkDependency()) {
        watchedProjectSdk = null
      }
      if (sdk != null) {
        removeTrackedJdk(sdk)
      }
    }

    private fun removeTrackedJdk(sdk: Sdk) {
      if (!hasDependencyOn(sdk) && watchedSdks.remove(sdk.rootProvider)) {
        sdk.rootProvider.removeRootSetChangedListener(rootSetChangeListener)
        eventDispatcher.multicaster.removedDependencyOn(sdk)
        if (watchedSdks.isEmpty()) {
          eventDispatcher.multicaster.lastDependencyOnSdkRemoved()
        }
      }
    }

    override fun projectJdkChanged() {
      if (hasProjectSdkDependency()) {
        watchedProjectSdk?.let { removeTrackedJdk(it) }
        watchedProjectSdk = projectRootManager.projectSdk
        watchedProjectSdk?.let { addTrackedJdk(it) }
      }
    }

    fun hasProjectSdkDependency(): Boolean {
      return sdkDependencies.get(ModuleDependencyItem.InheritedSdkDependency).isNotEmpty()
    }

    private val projectRootManager by lazy { ProjectRootManager.getInstance(project) }

    private fun findSdk(sdkDependency: ModuleDependencyItem): Sdk? = when (sdkDependency) {
      is ModuleDependencyItem.InheritedSdkDependency -> projectRootManager.projectSdk
      is ModuleDependencyItem.SdkDependency -> ModifiableRootModelBridge.findSdk(sdkDependency.sdkName, sdkDependency.sdkType)
      else -> null
    }

    fun hasDependencyOn(jdk: Sdk): Boolean {
      return sdkDependencies.get(ModuleDependencyItem.SdkDependency(jdk.name, jdk.sdkType.name)).isNotEmpty()
             || isProjectSdk(jdk) && hasProjectSdkDependency()
    }

    private fun isProjectSdk(jdk: Sdk) =
      jdk.name == projectRootManager.projectSdkName && jdk.sdkType.name == projectRootManager.projectSdkTypeName

    fun unsubscribe() {
      watchedSdks.forEach {
        it.removeRootSetChangedListener(rootSetChangeListener)
      }
      watchedSdks.clear()
    }
  }

  override fun reset() {
    libraryTablesListener.unsubscribe()
    jdkChangeListener.unsubscribe()
    setupTrackedLibrariesAndJdks()
  }

  fun workspaceModelChanged(event: VersionedStorageChange){
    if (project.isDisposed) return

    // Roots changed event should be fired for the global libraries linked with module
    val moduleChanges = event.getChanges(ModuleEntity::class.java)
    for (change in moduleChanges) {
      change.oldEntity?.let { removeTrackedLibrariesAndJdkFromEntity(it) }
      change.newEntity?.let { addTrackedLibraryAndJdkFromEntity(it) }
    }
  }

}