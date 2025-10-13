package com.intellij.grazie.ide.ui.widget

import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager

internal class GrazieStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String {
    return widgetId
  }

  override fun getDisplayName(): String {
    return GrazieBundle.message("grazie.status.bar.widget.name")
  }

  override fun isAvailable(project: Project): Boolean {
    return Registry.`is`("grazie.enable.status.bar.widget", false) && !GrazieCloudConnector.hasAdditionalConnectors()
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

  companion object {
    const val widgetId = "GrazieStatusBarWidget"

    internal fun findFactory(project: Project): GrazieStatusBarWidgetFactory? {
      @Suppress("IncorrectServiceRetrieving")
      val manager = project.service<StatusBarWidgetsManager>()
      return manager.findWidgetFactory(widgetId) as? GrazieStatusBarWidgetFactory
    }
  }
}
