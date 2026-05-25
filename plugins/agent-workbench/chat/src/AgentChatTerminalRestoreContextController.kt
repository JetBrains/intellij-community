// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalRestoreContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.coroutines.coroutineContext

internal class AgentChatTerminalRestoreContextController(
  private val file: AgentChatVirtualFile,
  private val descriptor: AgentSessionProviderDescriptor?,
  parentDisposable: Disposable,
) : AgentChatDisposableController {
  private val disposable: Disposable = Disposer.newDisposable(parentDisposable, "AgentChatTerminalRestoreContextController")
  private var attachedTab: AgentChatTerminalTab? = null
  private var cwdSnapshotJob: Job? = null

  fun attach(tab: AgentChatTerminalTab) {
    if (attachedTab != null) return
    val descriptor = descriptor ?: return
    if (!descriptor.supportsTerminalRestoreContext) {
      return
    }
    attachedTab = tab
    installRestoreContextComponent(tab = tab, descriptor = descriptor)
    cwdSnapshotJob = tab.coroutineScope.launch {
      while (coroutineContext.isActive) {
        delay(CWD_SNAPSHOT_INTERVAL_MS)
        recordWorkingDirectory(tab = tab, descriptor = descriptor)
      }
    }
  }

  override fun dispose() {
    cwdSnapshotJob?.cancel()
    cwdSnapshotJob = null
    val tab = attachedTab
    val descriptor = descriptor
    attachedTab = null
    if (tab != null && descriptor != null && descriptor.supportsTerminalRestoreContext) {
      recordWorkingDirectory(tab = tab, descriptor = descriptor)
    }
    Disposer.dispose(disposable)
  }

  private fun recordWorkingDirectory(tab: AgentChatTerminalTab, descriptor: AgentSessionProviderDescriptor) {
    val workingDirectory = runCatching { tab.terminalView?.getCurrentDirectory() }.getOrNull()
    if (workingDirectory.isNullOrBlank()) return
    descriptor.recordTerminalWorkingDirectory(
      path = file.projectPath,
      threadId = file.threadId,
      workingDirectory = workingDirectory,
    )
  }

  private fun installRestoreContextComponent(tab: AgentChatTerminalTab, descriptor: AgentSessionProviderDescriptor) {
    val terminalView = tab.terminalView ?: return
    val context = descriptor.readTerminalRestoreContext(path = file.projectPath, threadId = file.threadId) ?: return
    val text = buildTerminalRestoreContextText(context)
    if (text.isBlank()) {
      return
    }
    terminalView.setTopComponent(createTerminalRestoreContextComponent(text), disposable)
  }
}

internal fun buildTerminalRestoreContextText(context: AgentSessionTerminalRestoreContext): String {
  val workingDirectory = context.workingDirectory
  if (workingDirectory.isNullOrBlank()) return ""
  return AgentChatBundle.message("chat.terminal.restore.context.working.directory", workingDirectory)
}

private fun createTerminalRestoreContextComponent(text: String): JComponent {
  val title = JBLabel(AgentChatBundle.message("chat.terminal.restore.context.title"))
  val textArea = JBTextArea().apply {
    this.text = text
    isEditable = false
    isFocusable = false
    lineWrap = true
    wrapStyleWord = false
    background = UIUtil.getPanelBackground()
    border = JBUI.Borders.emptyTop(4)
    accessibleContext.accessibleName = AgentChatBundle.message("chat.terminal.restore.context.accessible.name")
  }
  return JPanel(BorderLayout()).apply {
    border = JBUI.Borders.compound(
      JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
      JBUI.Borders.empty(6, 8),
    )
    add(title, BorderLayout.NORTH)
    add(textArea, BorderLayout.CENTER)
    accessibleContext.accessibleName = title.text
  }
}

private const val CWD_SNAPSHOT_INTERVAL_MS = 60_000L
