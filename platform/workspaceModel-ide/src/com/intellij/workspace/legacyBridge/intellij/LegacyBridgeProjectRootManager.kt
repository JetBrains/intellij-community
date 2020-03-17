package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.impl.ProjectRootManagerComponent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.workspace.api.EntityStoreChanged
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
        getBatchSession(false).beforeRootsChanged()
      }

      override fun changed(event: EntityStoreChanged) {
        if (myProject.isDisposed || Disposer.isDisposing(myProject)) return
        LOG.bracket("ProjectRootManager.EntityStoreChange") {
          getBatchSession(false).rootsChanged()
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
}
