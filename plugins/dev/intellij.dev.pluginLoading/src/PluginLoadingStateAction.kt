// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.pluginLoading

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.dev.pluginLoading.PluginLoadingStateTreeBuilder.buildPluginLoadingStateTree
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows the load state of every plugin and of every content module of the current process.
 *
 * The action reads the plugin set that [PluginManagerCore] keeps after the startup. So the dialog shows the same data
 * as the `Plugin set resolution:` block in the log, but as a tree.
 */
internal class PluginLoadingStateAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    e.coroutineScope.launch {
      val pluginSet = PluginManagerCore.getPluginSet()
      val stateTree = withContext(Dispatchers.Default) {
        buildPluginLoadingStateTree(pluginSet, problemsOnly = false)
      }
      withContext(Dispatchers.EDT) {
        PluginLoadingStateDialog(project, stateTree).show()
      }
    }
  }
}
