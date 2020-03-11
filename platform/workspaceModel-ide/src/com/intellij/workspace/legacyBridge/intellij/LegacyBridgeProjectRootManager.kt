package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.InheritedJdkOrderEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.RootProvider.RootSetChangedListener
import com.intellij.openapi.roots.impl.ProjectRootManagerComponent
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.workspace.api.*
import com.intellij.workspace.bracket
import com.intellij.workspace.ide.WorkspaceModelChangeListener
import com.intellij.workspace.ide.WorkspaceModelTopics

@Suppress("ComponentNotRegistered")
class LegacyBridgeProjectRootManager(project: Project) : ProjectRootManagerComponent(project) {
  private val LOG = Logger.getInstance(javaClass)
  private val rootChangedListener = RootChangedListener()
  private var globalLibraryTableListener: GlobalLibraryTableListener? = null
  private val rootProviderPerModule = mutableMapOf<RootProvider, MutableSet<ModuleEntity>>()

  init {
    val bus = project.messageBus.connect(this)

    WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(bus, object : WorkspaceModelChangeListener {
      override fun beforeChanged(event: EntityStoreChanged) {
        if (myProject.isDisposed || Disposer.isDisposing(myProject)) return

        val performUpdate = processChanges(event, project)

        if (performUpdate) myRootsChanged.beforeRootsChanged()
      }

      override fun changed(event: EntityStoreChanged) {
        if (myProject.isDisposed || Disposer.isDisposing(myProject)) return
        LOG.bracket("ProjectRootManager.EntityStoreChange") {

          val performUpdate = processChanges(event, project)
          if (performUpdate) myRootsChanged.rootsChanged()

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
        jdk.rootProvider.addRootSetChangedListener(listener, this@LegacyBridgeProjectRootManager)
      }

      override fun jdkNameChanged(jdk: Sdk, previousName: String) {
      }

      override fun jdkRemoved(jdk: Sdk) {
        jdk.rootProvider.removeRootSetChangedListener(listener)
      }
    })
  }

  override fun getRootsChangeRunnable(): Runnable {
    return Runnable { if (hasModuleWithInheritedJdk()) makeRootsChange(EmptyRunnable.INSTANCE, false, true) }
  }

  override fun projectClosed() {
    super.projectClosed()
    unsubscribeListeners()
  }

  override fun dispose() {
    super.dispose()
    unsubscribeListeners()
  }

  private fun unsubscribeListeners() {
    val globalLibraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    globalLibraryTableListener?.let { globalLibraryTable.removeListener(it) }
    globalLibraryTableListener = null
    rootProviderPerModule.keys.forEach { it.removeRootSetChangedListener(rootChangedListener) }
    rootProviderPerModule.clear()
  }

  // Library changes should not fire any events if the library is not included in any of order entries
  private fun processChanges(events: EntityStoreChanged, project: Project): Boolean {
    val libraryChanges = events.getChanges(LibraryEntity::class.java)
    return if (libraryChanges.isNotEmpty() && libraryChanges.count() == events.getAllChanges().count()) {
      for (event in libraryChanges) {
        val res = when (event) {
          is EntityChange.Added -> libraryHasOrderEntry(event.entity.name, project)
          is EntityChange.Removed -> libraryHasOrderEntry(event.entity.name, project)
          is EntityChange.Replaced -> libraryHasOrderEntry(event.newEntity.name, project)
        }
        if (res) return true
      }
      return false
    } else true
  }

  private fun addTrackedLibraryFromEntity(moduleEntity: ModuleEntity) {
    val globalLibraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    moduleEntity.dependencies.filterIsInstance<ModuleDependencyItem.Exportable.LibraryDependency>()
      .filter { it.library.tableId is LibraryTableId.GlobalLibraryTableId }
      .forEach {
        val libraryName = it.library.name
        if (globalLibraryTableListener == null) {
          globalLibraryTableListener = GlobalLibraryTableListener()
          globalLibraryTable.addListener(globalLibraryTableListener!!)
        }
        globalLibraryTableListener?.addTrackedLibrary(moduleEntity, libraryName)

        val library = globalLibraryTable.getLibraryByName(libraryName)
        if (library !is RootProvider) return@forEach
        if (!rootProviderPerModule.contains(library)) library.addRootSetChangedListener(rootChangedListener)
        rootProviderPerModule.computeIfAbsent(library) { mutableSetOf() }.add(moduleEntity)
      }
  }

  private fun unTrackLibraryFromEntity(moduleEntity: ModuleEntity) {
    val globalLibraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    moduleEntity.dependencies.filterIsInstance<ModuleDependencyItem.Exportable.LibraryDependency>()
      .filter { it.library.tableId is LibraryTableId.GlobalLibraryTableId }
      .forEach {
        val libraryName = it.library.name
        globalLibraryTableListener?.let { listener ->
          listener.unTrackLibrary(moduleEntity, libraryName)
          if (listener.isEmpty()) {
            globalLibraryTable.removeListener(listener)
            globalLibraryTableListener = null
          }
        }

        val library = globalLibraryTable.getLibraryByName(libraryName)
        if (library !is RootProvider) return@forEach
        rootProviderPerModule[library]?.let { modules ->
          modules.remove(moduleEntity)
          if (modules.isEmpty()) {
            library.removeRootSetChangedListener(rootChangedListener)
            rootProviderPerModule.remove(library)
          }
        }
      }
  }

  private fun hasModuleWithInheritedJdk() = ModuleManager.getInstance(project).modules.asSequence()
    .filter { ModuleRootManager.getInstance(it).orderEntries.filterIsInstance<InheritedJdkOrderEntry>().any() }.any()

  private fun libraryHasOrderEntry(name: String, project: Project): Boolean {
    ModuleManager.getInstance(project).modules.forEach { module ->
      val exists = ModuleRootManager.getInstance(module).orderEntries.any { it is LibraryOrderEntry && it.libraryName == name }
      if (exists) return true
    }
    return false
  }

  // Listener for global libraries linked to module
  private inner class GlobalLibraryTableListener : LibraryTable.Listener {
    private val librariesPerModuleMap = mutableMapOf<ModuleEntity, MutableSet<String>>()

    fun addTrackedLibrary(moduleEntity: ModuleEntity, libraryName: String) {
      librariesPerModuleMap.computeIfAbsent(moduleEntity) { mutableSetOf() }.add(libraryName)
    }

    fun unTrackLibrary(moduleEntity: ModuleEntity, libraryName: String) {
      librariesPerModuleMap[moduleEntity]?.let { libraries ->
        libraries.remove(libraryName)
        if (libraries.isEmpty()) librariesPerModuleMap.remove(moduleEntity)
      }
    }

    fun isEmpty() = librariesPerModuleMap.isEmpty()

    override fun afterLibraryAdded(newLibrary: Library) {
      if (librariesSet().contains(newLibrary.name)) makeRootsChange(EmptyRunnable.INSTANCE, false, true)
    }

    override fun afterLibraryRemoved(library: Library) {
      if (librariesSet().contains(library.name)) makeRootsChange(EmptyRunnable.INSTANCE, false, true)
    }

    private fun librariesSet() = librariesPerModuleMap.values.flatten().toSet()
  }

  private inner class RootChangedListener : RootSetChangedListener {
    private var insideRootsChange = false

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
  }
}
