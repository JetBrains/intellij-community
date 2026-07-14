// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.plugins

import com.intellij.ide.plugins.PluginPermissionRequest
import com.intellij.ide.plugins.PluginPermissionService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.updateSettings.AccessPluginClassLoadersRequest
import com.intellij.openapi.updateSettings.DisablePluginRequest
import com.intellij.openapi.updateSettings.EnablePluginRequest
import com.intellij.openapi.updateSettings.InstallPluginRequest
import com.intellij.openapi.updateSettings.PluginManagementAction
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.Action
import javax.swing.JComponent

/**
 * Shows a non-modal dialog that sends each type of [PluginPermissionRequest] to [PluginPermissionService].
 */
internal class PluginPermissionServiceDemoAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    PluginPermissionServiceDemoDialog(e.project).show()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

@Suppress("HardCodedStringLiteral", "SplitModeApiUsage")
private class PluginPermissionServiceDemoDialog(project: Project?) : DialogWrapper(project, false) {
  private val pluginIdField = JBTextField(DEFAULT_PLUGIN_ID)
  private val logArea = JBTextArea(15, 80).apply { isEditable = false }

  init {
    title = "Plugin Permission Service Demo"
    isModal = false
    init()
  }

  override fun createCenterPanel(): JComponent = panel {
    row("Plugin ID:") {
      cell(pluginIdField).align(Align.FILL)
    }
    row {
      button("Request Enable") {
        request { EnablePluginRequest(it, "The demo asks to enable the plugin '${it.idString}'.") }
      }
      button("Request Disable") {
        request { DisablePluginRequest(it, "The demo asks to disable the plugin '${it.idString}'.") }
      }
      button("Request Install") {
        request { InstallPluginRequest(it, "The demo asks to install the plugin '${it.idString}'.") }
      }
    }
    row {
      button("Request Plugin Class Loaders") {
        requestClassLoaders()
      }
      button("Request Disable from Java") {
        requestDisableFromJava()
      }
    }
    row {
      scrollCell(logArea).align(Align.FILL)
    }.resizableRow()
  }

  override fun createActions(): Array<Action> = arrayOf(cancelAction)

  private fun request(createRequest: (PluginId) -> PluginPermissionRequest<PluginManagementAction>) {
    val request = createRequest(PluginId.getId(pluginIdField.text.trim()))
    log("Send ${request.javaClass.simpleName}")
    demoScope().launch {
      val result = PluginPermissionService.getInstance().withPermission(request) { action ->
        action.apply()
        "the operation started"
      }
      logResult(request, result)
    }
  }

  private fun requestClassLoaders() {
    val request = AccessPluginClassLoadersRequest("The demo asks to read the plugin descriptors and their class loaders.")
    log("Send ${request.javaClass.simpleName}")
    demoScope().launch {
      val result = PluginPermissionService.getInstance().withPermission(request) { action ->
        val descriptors = action.getPluginDescriptors()
        val loaded = descriptors.count { it.pluginClassLoader != null }
        "${descriptors.size} plugins, $loaded with a class loader"
      }
      logResult(request, result)
    }
  }

  private fun requestDisableFromJava() {
    val pluginId = PluginId.getId(pluginIdField.text.trim())
    log("Send DisablePluginRequest from Java")
    PluginPermissionServiceJavaDemo.requestDisable(pluginId).whenComplete { result, error ->
      demoScope().launch(Dispatchers.EDT) {
        if (error == null) log("Java request is allowed: $result")
        else log("Java request fails: $error")
      }
    }
  }

  private suspend fun logResult(request: PluginPermissionRequest<*>, result: Result<String>) {
    withContext(Dispatchers.EDT) {
      result
        .onSuccess { log("${request.javaClass.simpleName} is allowed: $it") }
        .onFailure { log("${request.javaClass.simpleName} fails: $it") }
    }
  }

  private fun log(message: String) {
    logArea.append(message + "\n")
  }

  private fun demoScope(): CoroutineScope = service<PluginPermissionServiceDemoScope>().coroutineScope
}

private const val DEFAULT_PLUGIN_ID = "org.jetbrains.plugins.yaml"

@Service
private class PluginPermissionServiceDemoScope(val coroutineScope: CoroutineScope)
