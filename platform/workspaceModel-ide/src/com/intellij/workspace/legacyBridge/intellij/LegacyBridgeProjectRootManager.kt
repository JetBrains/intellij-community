package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.ProjectRootManagerComponent
import com.intellij.workspace.api.EntityStoreChanged
import com.intellij.workspace.bracket
import com.intellij.workspace.ide.WorkspaceModelChangeListener
import com.intellij.workspace.ide.WorkspaceModelTopics

class LegacyBridgeProjectRootManager(project: Project) : ProjectRootManagerComponent(project) {
  private val LOG = Logger.getInstance(javaClass)

  init {
    project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun beforeChanged(event: EntityStoreChanged) {
        getBatchSession(false).beforeRootsChanged()
      }

      override fun changed(event: EntityStoreChanged) = LOG.bracket("ProjectRootManager.EntityStoreChange") {
        getBatchSession(false).rootsChanged()
      }
    })
  }
}
