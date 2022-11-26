package org.jetbrains.completion.full.line.platform.diagnostics

import com.intellij.diagnostic.logging.DefaultLogFilterModel
import com.intellij.diagnostic.logging.LogConsoleBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import java.io.File

class DiagnosticsLogConsole(private val project: Project) :
  LogConsoleBase(project, null, message("full.line.diagnostics.tab.title"), true, DefaultLogFilterModel(project)), BaseLogConsole {
  override fun isActive(): Boolean {
    return ToolWindowManager.getInstance(project).getToolWindow(FULL_LINE_TOOL_WINDOW_ID)?.isVisible ?: false
  }

  override fun addMessage(message: DiagnosticsListener.Message) {
    val msg = prepareMessage(message)
    super.addMessage(msg)
  }

  override fun dispose() {
    storeLog(originalDocument?.toString())
    super.dispose()
  }
}

class InMemoryLogConsole : BaseLogConsole {
  val content = StringBuilder()

  override fun addMessage(message: DiagnosticsListener.Message) {
    val msg = prepareMessage(message)
    content.append(msg).append("\n")
  }

  override fun dispose() {
    storeLog(content.toString())
  }
}

interface BaseLogConsole : Disposable {
  fun addMessage(message: DiagnosticsListener.Message)

  fun prepareMessage(message: DiagnosticsListener.Message): String {
    val time = DateFormatUtil.formatTime(message.time)
    val part = message.part.toString().padStart(PAD_COUNT)
    return "[ $time | $part ] ${message.text}"
  }

  fun storeLog(content: String?) {
    Registry.get("full.line.diagnostics.file").asString().takeIf { it.isNotEmpty() }?.let {
      val logFile = File(it)
      if (!logFile.exists()) logFile.createNewFile()

      content?.let(logFile::appendText)
    }
  }

  private companion object {
    val PAD_COUNT = FullLinePart.values().maxOf { it.name.length }
  }
}
