// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.InheritedJdkOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.RootProvider.RootSetChangedListener
import com.intellij.openapi.roots.impl.ProjectRootManagerComponent
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar.APPLICATION_LEVEL
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChanged
import com.intellij.workspaceModel.ide.impl.bracket
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.levelToLibraryTableId
import com.intellij.workspaceModel.storage.bridgeEntities.*

@Suppress("ComponentNotRegistered")
class ProjectRootManagerBridge(project: Project) : ProjectRootManagerComponent(project) {
  companion object {
    private const val LIBRARY_NAME_DELIMITER = ":"
  }

  private val LOG = Logger.getInstance(javaClass)
  private val globalLibraryTableListener = GlobalLibraryTableListener()

  init {
    val bus = project.messageBus.connect(this)

    WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(bus, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChanged) {
        if (myProject.isDisposed || Disposer.isDisposing(myProject)) return
        LOG.bracket("ProjectRootManager.EntityStoreChange") {
          // Roots changed even should be fired for the global libraries linked with module
          val moduleChanges = event.getChanges(ModuleEntity::class.java)
          for (change in moduleChanges) {
            when (change) {
              is EntityChange.Added -> addTrackedLibraryFromEntity(change.entity)
              is EntityChange.Removed -> unTrackLibraryFromEntity(change.entity)
              is EntityChange.Replaced -> {
                unTrackLibraryFromEntity(change.oldEntity)
                addTrackedLibraryFromEntity(change.newEntity)
              }
            }
          }
        }
      }
    })

    val listener = RootSetChangedListener {
      makeRootsChange(EmptyRunnable.INSTANCE, false, true)
    }

    // TODO It's also possible not to fire roots change event if JDK is not used
    bus.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : ProjectJdkTable.Listener {
      override fun jdkAdded(jdk: Sdk) {
        jdk.rootProvider.addRootSetChangedListener(listener, this@ProjectRootManagerBridge)
      }

      override fun jdkNameChanged(jdk: Sdk, previousName: String) {
        //todo make this more efficient by storing mapping between sdks and modules
        val affectedModules = WorkspaceModel.getInstance(myProject).entityStorage.current.entities(ModuleEntity::class.java)
          .filter { module ->
            module.dependencies.asSequence().filterIsInstance<ModuleDependencyItem.SdkDependency>().any {
              it.sdkName == previousName && it.sdkType == jdk.sdkType.name
            }
          }.toList()
        if (affectedModules.isNotEmpty()) {
          WorkspaceModel.getInstance(myProject).updateProjectModel { builder ->
            for (module in affectedModules) {
              val updated = module.dependencies.map {
                when (it) {
                  is ModuleDependencyItem.SdkDependency -> ModuleDependencyItem.SdkDependency(jdk.name, jdk.sdkType.name)
                  else -> it
                }
              }
              builder.modifyEntity(ModifiableModuleEntity::class.java, module) {
                dependencies = updated
              }
            }
          }
        }
      }

      override fun jdkRemoved(jdk: Sdk) {
        jdk.rootProvider.removeRootSetChangedListener(listener)
      }
    })
  }

  override fun getActionToRunWhenProjectJdkChanges(): Runnable {
    return Runnable {
      super.getActionToRunWhenProjectJdkChanges().run()
      if (hasModuleWithInheritedJdk()) makeRootsChange(EmptyRunnable.INSTANCE, false, true) }
  }

  override fun projectClosed() {
    super.projectClosed()
    unsubscribeListeners()
  }

  override fun dispose() {
    super.dispose()
    unsubscribeListeners()
  }

  internal fun fireRootsChanged(isBefore: Boolean) {
    if (isBefore) myRootsChanged.beforeRootsChanged() else myRootsChanged.rootsChanged()
  }

  private fun unsubscribeListeners() {
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    val globalLibraryTable = libraryTablesRegistrar.libraryTable
    globalLibraryTableListener.getLibraryLevels().forEach { libraryLevel ->
      val libraryTable = when (libraryLevel) {
        APPLICATION_LEVEL -> globalLibraryTable
        else -> libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project)
      }
      libraryTable?.libraryIterator?.forEach { (it as? RootProvider)?.removeRootSetChangedListener(globalLibraryTableListener) }
      libraryTable?.removeListener(globalLibraryTableListener)
    }
    globalLibraryTableListener.clear()
  }

  private fun addTrackedLibraryFromEntity(moduleEntity: ModuleEntity) {
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    moduleEntity.dependencies.filterIsInstance<ModuleDependencyItem.Exportable.LibraryDependency>()
      .filter { it.library.tableId is LibraryTableId.GlobalLibraryTableId }
      .forEach {
        val libraryName = it.library.name
        val libraryLevel = it.library.tableId.level
        val libraryTable = libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project) ?: return@forEach
        if (globalLibraryTableListener.isEmpty(libraryLevel)) libraryTable.addListener(globalLibraryTableListener)
        globalLibraryTableListener.addTrackedLibrary(moduleEntity, libraryTable, libraryName)
      }
  }

  private fun unTrackLibraryFromEntity(moduleEntity: ModuleEntity) {
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    moduleEntity.dependencies.filterIsInstance<ModuleDependencyItem.Exportable.LibraryDependency>()
      .filter { it.library.tableId is LibraryTableId.GlobalLibraryTableId }
      .forEach {
        val libraryName = it.library.name
        val libraryLevel = it.library.tableId.level
        val libraryTable = libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project) ?: return@forEach
        globalLibraryTableListener.unTrackLibrary(moduleEntity, libraryTable, libraryName)
        if (globalLibraryTableListener.isEmpty(libraryLevel)) libraryTable.removeListener(globalLibraryTableListener)
      }
  }

  private fun hasModuleWithInheritedJdk() = ModuleManager.getInstance(project).modules.asSequence()
    .filter { ModuleRootManager.getInstance(it).orderEntries.filterIsInstance<InheritedJdkOrderEntry>().any() }.any()

  // Listener for global libraries linked to module
  private inner class GlobalLibraryTableListener : LibraryTable.Listener, RootSetChangedListener {
    private val librariesPerModuleMap = BidirectionalMultiMap<ModuleEntity, String>()
    private var insideRootsChange = false

    fun addTrackedLibrary(moduleEntity: ModuleEntity, libraryTable: LibraryTable, libraryName: String) {
      val library = libraryTable.getLibraryByName(libraryName)
      val libraryIdentifier = getLibraryIdentifier(libraryTable, libraryName)
      if (!librariesPerModuleMap.containsValue(libraryIdentifier)) {
        (library as? RootProvider)?.addRootSetChangedListener(this)
      }
      librariesPerModuleMap.put(moduleEntity, libraryIdentifier)
    }

    fun unTrackLibrary(moduleEntity: ModuleEntity, libraryTable: LibraryTable, libraryName: String) {
      val library = libraryTable.getLibraryByName(libraryName)
      val libraryIdentifier = getLibraryIdentifier(libraryTable, libraryName)
      librariesPerModuleMap.remove(moduleEntity, libraryIdentifier)
      if (!librariesPerModuleMap.containsValue(libraryIdentifier)) {
        (library as? RootProvider)?.removeRootSetChangedListener(this)
      }
    }

    fun isEmpty(libraryLevel: String) = librariesPerModuleMap.values.none{ it.startsWith("$libraryLevel$LIBRARY_NAME_DELIMITER") }

    fun getLibraryLevels() = librariesPerModuleMap.values.mapTo(HashSet()) { it.substringBefore(LIBRARY_NAME_DELIMITER) }

    override fun afterLibraryAdded(newLibrary: Library) {
      if (librariesPerModuleMap.containsValue(getLibraryIdentifier(newLibrary))) makeRootsChange(EmptyRunnable.INSTANCE, false, true)
    }

    override fun afterLibraryRemoved(library: Library) {
      if (librariesPerModuleMap.containsValue(getLibraryIdentifier(library))) makeRootsChange(EmptyRunnable.INSTANCE, false, true)
    }

    override fun afterLibraryRenamed(library: Library, oldName: String?) {
      val libraryTable = library.table
      val newName = library.name
      if (libraryTable != null && oldName != null && newName != null) {
        val affectedModules = librariesPerModuleMap.getKeys(getLibraryIdentifier(libraryTable, oldName))
        if (affectedModules.isNotEmpty()) {
          val libraryTableId = levelToLibraryTableId(libraryTable.tableLevel)
          WorkspaceModel.getInstance(myProject).updateProjectModel { builder ->
            //maybe it makes sense to simplify this code by reusing code from PEntityStorageBuilder.updateSoftReferences
            affectedModules.mapNotNull { builder.resolve(it.persistentId()) }.forEach { module ->
              val updated = module.dependencies.map {
                when {
                  it is ModuleDependencyItem.Exportable.LibraryDependency && it.library.tableId == libraryTableId && it.library.name == oldName ->
                    it.copy(library = LibraryId(newName, libraryTableId))
                  else -> it
                }
              }
              builder.modifyEntity(ModifiableModuleEntity::class.java, module) {
                dependencies = updated
              }
            }
          }
        }
      }
    }

    override fun rootSetChanged(wrapper: RootProvider) {
      if (insideRootsChange) return
      insideRootsChange = true
      try {
        makeRootsChange(EmptyRunnable.INSTANCE, false, true)
      }
      finally {
        insideRootsChange = false
      }
    }

    private fun getLibraryIdentifier(library: Library) = "${library.table.tableLevel}$LIBRARY_NAME_DELIMITER${library.name}"
    private fun getLibraryIdentifier(libraryTable: LibraryTable, libraryName: String) = "${libraryTable.tableLevel}$LIBRARY_NAME_DELIMITER$libraryName"

    fun clear() = librariesPerModuleMap.clear()
  }
}
