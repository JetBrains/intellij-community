package com.intellij.remoteServer.ir.configuration

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.remoteServer.ir.target.getAdaptedRemoteRunnersConfigurables

class RemoteRunnersEditor(private val project: Project?) : MasterDetailsComponent() {
  init {
    // note that `MasterDetailsComponent` does not work without `initTree()`
    initTree()
  }

  override fun getDisplayName(): String = "Remote Runners"

  override fun reset() {
    val configurables = getAdaptedRemoteRunnersConfigurables(project)

    clearChildren()

    configurables.forEach { configurable ->
      addNode(MyNode(configurable), myRoot)
    }

    super.reset()
  }
}