package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.impl.ProjectRootManagerComponent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.workspace.api.EntityChange
import com.intellij.workspace.api.EntityStoreChanged
import com.intellij.workspace.api.LibraryEntity
import com.intellij.workspace.bracket
import com.intellij.workspace.ide.WorkspaceModelChangeListener
import com.intellij.workspace.ide.WorkspaceModelTopics

@Suppress("ComponentNotRegistered")
class LegacyBridgeProjectRootManager(project: Project) : ProjectRootManagerComponent(project) {
  private val LOG = Logger.getInstance(javaClass)

  init {
    val bus = project.messageBus.connect(this)

    bus.subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun beforeChanged(event: EntityStoreChanged) {
        if (myProject.isDisposed || Disposer.isDisposing(myProject)) return

        val performUpdate = processChanges(event, project)

        if (performUpdate) getBatchSession(false).beforeRootsChanged()
      }

      override fun changed(event: EntityStoreChanged) {
        if (myProject.isDisposed || Disposer.isDisposing(myProject)) return
        LOG.bracket("ProjectRootManager.EntityStoreChange") {

          val performUpdate = processChanges(event, project)

          if (performUpdate) getBatchSession(false).rootsChanged()
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

  private fun libraryHasOrderEntry(name: String, project: Project): Boolean {
    ModuleManager.getInstance(project).modules.forEach { module ->
      val exists = ModuleRootManager.getInstance(module).orderEntries.any { it is LibraryOrderEntry && it.libraryName == name }
      if (exists) return true
    }
    return false
  }
}
