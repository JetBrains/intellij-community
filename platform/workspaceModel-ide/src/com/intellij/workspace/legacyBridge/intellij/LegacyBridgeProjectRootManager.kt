package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.ProjectRootManagerComponent
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.workspace.api.EntityChange
import com.intellij.workspace.api.EntityStoreChanged
import com.intellij.workspace.api.LibraryEntity
import com.intellij.workspace.bracket
import com.intellij.workspace.ide.WorkspaceModelChangeListener
import com.intellij.workspace.ide.WorkspaceModelTopics
import java.util.*

@Suppress("ComponentNotRegistered")
class LegacyBridgeProjectRootManager(project: Project) : ProjectRootManagerComponent(project) {
  private val LOG = Logger.getInstance(javaClass)

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
        }
      }
    })

    val listener = RootProvider.RootSetChangedListener {
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
    return Runnable {
      if (hasModuleWithInheritedJdk()) {
        myRootsChanged.beforeRootsChanged()
        myRootsChanged.rootsChanged()
      }
    }
  }

  // Listener that increments modification count should be added to each libraryTable that contains a library included in any orderEntry
  fun addListenerForTable(libraryTable: LibraryTable): Disposable {
    synchronized(myLibraryTableListenersLock) {
      var multiListener = myLibraryTableMultiListeners[libraryTable]
      if (multiListener == null) {
        multiListener = LibraryTableMultiListener()
        libraryTable.addListener(multiListener)
        myLibraryTableMultiListeners[libraryTable] = multiListener
      }
      multiListener.incListener()

      return Disposable { removeListenerForTable(libraryTable) }
    }
  }

  private fun removeListenerForTable(libraryTable: LibraryTable) {
    synchronized(myLibraryTableListenersLock) {
      val multiListener = myLibraryTableMultiListeners[libraryTable]
      if (multiListener != null) {
        val last = multiListener.decListener()
        if (last) {
          libraryTable.removeListener(multiListener)
          myLibraryTableMultiListeners.remove(libraryTable)
        }
      }
    }
  }

  private val myLibraryTableListenersLock = Any()
  private val myLibraryTableMultiListeners: MutableMap<LibraryTable, LibraryTableMultiListener> = HashMap()

  private inner class LibraryTableMultiListener : LibraryTable.Listener {
    private var counter = 0

    @Synchronized
    fun incListener() {
      counter++
    }

    @Synchronized
    fun decListener(): Boolean = --counter == 0

    override fun afterLibraryAdded(newLibrary: Library) = incModificationCount()

    override fun afterLibraryRenamed(library: Library) = incModificationCount()

    override fun beforeLibraryRemoved(library: Library) = incModificationCount()

    override fun afterLibraryRemoved(library: Library) = incModificationCount()
  }

  /** Library changes should not fire any events if the library is not included in any of order entries */
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

  private fun hasModuleWithInheritedJdk() = ModuleManager.getInstance(project).modules.asSequence()
    .filter { ModuleRootManager.getInstance(it).orderEntries.filterIsInstance<InheritedJdkOrderEntry>().any() }.any()

  private fun libraryHasOrderEntry(name: String, project: Project): Boolean {
    ModuleManager.getInstance(project).modules.forEach { module ->
      val exists = ModuleRootManager.getInstance(module).orderEntries.any { it is LibraryOrderEntry && it.libraryName == name }
      if (exists) return true
    }
    return false
  }
}
