package org.jetbrains.completion.full.line.platform.diagnostics

import com.intellij.diagnostic.logging.DefaultLogFilterModel
import com.intellij.diagnostic.logging.LogConsoleBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message

class DiagnosticsLogConsole(private val project: Project) :
  LogConsoleBase(project, null, message("full.line.diagnostics.tab.title"), true, DefaultLogFilterModel(project)) {
  override fun isActive(): Boolean {
    return ToolWindowManager.getInstance(project).getToolWindow(FULL_LINE_TOOL_WINDOW_ID)?.isVisible ?: false
  }

  fun addMessage(message: DiagnosticsListener.Message) {
    val time = DateFormatUtil.formatTime(message.time)
    val part = message.part.toString().padStart(PAD_COUNT)
    addMessage("[ $time | $part ] ${message.text}")
  }

  private companion object {
    val PAD_COUNT = FullLinePart.values().maxOf { it.name.length }
  }
}
