package org.jetbrains.completion.full.line.platform.diagnostics

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

internal const val FULL_LINE_TOOL_WINDOW_ID = "Full Line Diagnostics"

class FullLineDiagnosticsToolWindowFactory : ToolWindowFactory, DumbAware {
  @NlsSafe val title = "Log"

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val windowContent = FullLineToolWindowContent(project)
    val content = ContentFactory.SERVICE.getInstance()
      .createContent(windowContent.component, title, true)
    toolWindow.contentManager.apply {
      addContent(content)
      setSelectedContent(content)
    }
    toolWindow.setToHideOnEmptyContent(false)
  }

  override fun isApplicable(project: Project): Boolean {
    return Registry.`is`("full.line.enable.diagnostics")
  }
}

private class FullLineToolWindowContent(project: Project) : SimpleToolWindowPanel(false, true), Disposable {
  private val consoleLog: DiagnosticsLogConsole = DiagnosticsLogConsole(project)

  init {
    Disposer.register(this, consoleLog)
    setContent(consoleLog.component)
    DiagnosticsService.getInstance().subscribe(object : DiagnosticsListener {
      override fun messageReceived(message: DiagnosticsListener.Message) {
        consoleLog.addMessage(message)
      }
    }, this)
  }

  override fun dispose() {
  }
}
