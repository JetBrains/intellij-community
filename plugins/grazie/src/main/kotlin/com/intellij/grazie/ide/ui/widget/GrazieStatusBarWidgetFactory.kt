package com.intellij.grazie.ide.ui.widget

import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieConfig.State.Processing.Cloud
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.ide.msg.GrazieInitializerManager
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.util.application

internal class GrazieStatusBarWidgetFactory : StatusBarWidgetFactory, GrazieStateLifecycle {

  init { service<GrazieInitializerManager>().register(this) }

  override fun getId(): String {
    return widgetId
  }

  override fun getDisplayName(): String {
    return GrazieBundle.message("grazie.status.bar.widget.name")
  }

  override fun isAvailable(project: Project): Boolean {
    return Registry.`is`("grazie.enable.status.bar.widget", false)
           && !GrazieCloudConnector.hasAdditionalConnectors()
           && GrazieConfig.get().explicitlyChosenProcessing == Cloud
  }

  override fun createWidget(project: Project): StatusBarWidget {
    return GrazieStatusBarWidget(project)
  }

  override fun disposeWidget(widget: StatusBarWidget) {
    Disposer.dispose(widget)
  }

  override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
    return true
  }

  override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
    application.invokeLater {
      if (newState.explicitlyChosenProcessing != prevState.explicitlyChosenProcessing && prevState.explicitlyChosenProcessing != null) {
        ProjectManager.getInstance().openProjects.forEach { project ->
          project.service<StatusBarWidgetsManager>().updateWidget(this)
        }
      }
    }
  }

  companion object {
    const val widgetId = "GrazieStatusBarWidget"
  }
}
