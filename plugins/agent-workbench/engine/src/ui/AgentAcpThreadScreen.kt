// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.ui

import com.intellij.agent.workbench.engine.core.RuntimeKind
import com.intellij.agent.workbench.engine.core.ThreadEventEnvelope
import com.intellij.agent.workbench.engine.core.ThreadCommand
import com.intellij.agent.workbench.engine.core.ThreadContextCompaction
import com.intellij.agent.workbench.engine.core.ThreadFileDiff
import com.intellij.agent.workbench.engine.core.ThreadId
import com.intellij.agent.workbench.engine.core.ThreadMessage
import com.intellij.agent.workbench.engine.core.ThreadPlan
import com.intellij.agent.workbench.engine.core.ThreadProjection
import com.intellij.agent.workbench.engine.core.ThreadStatus
import com.intellij.agent.workbench.engine.core.ThreadToolCall
import com.intellij.agent.workbench.engine.core.ThreadTranscriptEntry
import com.intellij.agent.workbench.engine.platform.EngineEvents
import com.intellij.agent.workbench.engine.platform.EngineProjectService
import com.intellij.agent.workbench.engine.platform.EnginePromptSender
import com.intellij.agent.workbench.engine.platform.EngineRuntimeConnector
import com.intellij.agent.workbench.engine.platform.EngineUnreadTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants

/**
 * Non-terminal, IDE-native screen for a Engine/ACP thread. Renders the live [ThreadProjection]
 * (status, title, transcript) from the Engine and re-renders on every projection update, so an ACP
 * (or mock/remote) session appears inside the same Agent Chat editor tab instead of a terminal.
 */
internal class AgentAcpThreadScreen(
  private val project: Project,
  private val threadId: ThreadId,
  parent: Disposable,
) : JPanel(BorderLayout()) {
  private val statusLabel = JBLabel().apply { font = JBFont.label().asBold() }
  private val subtitleLabel = JBLabel().apply { foreground = UIUtil.getContextHelpForeground() }
  private val transcriptPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    border = JBUI.Borders.empty(8)
    isOpaque = false
  }
  private val inputArea = JBTextArea(3, 0).apply {
    lineWrap = true
    wrapStyleWord = true
    border = JBUI.Borders.empty(6)
    emptyText.text = EngineBundle.message("acp.screen.input.hint")
    addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
          e.consume()
          sendCurrentInput()
        }
      }
    })
  }

  init {
    border = JBUI.Borders.empty(4)
    add(buildHeader(), BorderLayout.NORTH)
    add(
      JBScrollPane(
        transcriptPanel,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
      ).apply { border = JBUI.Borders.empty() },
      BorderLayout.CENTER,
    )
    add(buildInput(), BorderLayout.SOUTH)
    render(EngineProjectService.getInstance(project).projection(threadId))
    subscribe(parent)
    // Restored tabs may carry an ACP runtime binding but no in-memory owner yet; rehydrate it so input re-enables.
    EngineRuntimeConnector.connectAll(project, threadId, parent)
  }

  private fun buildInput(): JComponent {
    return JBScrollPane(
      inputArea,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
    ).apply {
      preferredSize = Dimension(0, JBUI.scale(72))
      border = JBUI.Borders.customLine(UIUtil.getBoundsColor(), 1, 0, 0, 0)
    }
  }

  private fun sendCurrentInput() {
    if (!inputArea.isEnabled) {
      LOG.info("[$threadId] ACP screen ignored send: input disabled")
      return
    }
    val text = inputArea.text.trim()
    if (text.isEmpty()) return
    val sender = EnginePromptSender.forThread(project, threadId)
    if (sender == null) {
      val projection = EngineProjectService.getInstance(project).projection(threadId)
      LOG.warn(
        "[$threadId] ACP screen cannot send: no prompt sender, " +
        "status=${projection.thread.status}, runtimeKind=${projection.thread.runtimeKind}, " +
        "binding=${projection.runtimeBinding}, transcriptSize=${projection.transcript.size}",
      )
      return
    }
    LOG.info("[$threadId] ACP screen sending prompt: textLength=${text.length}")
    inputArea.text = ""
    sender.sendPrompt(project, threadId, text)
  }

  override fun addNotify() {
    super.addNotify()
    // Opening/focusing the chat clears its "unread" attention badge.
    if (!project.isDisposed) EngineUnreadTracker.getInstance(project).markRead(threadId)
  }

  private fun buildHeader(): JComponent {
    val header = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = JBUI.Borders.compound(
        JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0),
        JBUI.Borders.empty(8, 12),
      )
      isOpaque = false
    }
    statusLabel.alignmentX = LEFT_ALIGNMENT
    subtitleLabel.alignmentX = LEFT_ALIGNMENT
    header.add(statusLabel)
    header.add(Box.createVerticalStrut(2))
    header.add(subtitleLabel)
    return header
  }

  private fun subscribe(parent: Disposable) {
    project.messageBus.connect(parent).subscribe(
      EngineEvents.TOPIC,
      object : EngineEvents {
        override fun eventAppended(event: ThreadEventEnvelope) {}

        override fun projectionUpdated(threadId: ThreadId) {
          if (threadId != this@AgentAcpThreadScreen.threadId) return
          ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
              render(EngineProjectService.getInstance(project).projection(threadId))
              // The user is looking at this thread, so any fresh agent output is already seen.
              if (isShowing) EngineUnreadTracker.getInstance(project).markRead(threadId)
            }
          }
        }
      },
    )
  }

  @Suppress("HardCodedStringLiteral")
  private fun render(projection: ThreadProjection) {
    val thread = projection.thread
    val title: @NlsSafe String = thread.title.ifBlank { threadId.value }
    statusLabel.text = title
    subtitleLabel.text = EngineBundle.message(
      "acp.screen.subtitle",
      thread.runtimeKind.name,
      humanize(thread.status.name),
    )

    transcriptPanel.removeAll()
    if (projection.transcript.isEmpty()) {
      transcriptPanel.add(emptyState(thread.status))
    }
    else {
      for (entry in projection.transcript) {
        transcriptPanel.add(transcriptRow(entry))
        transcriptPanel.add(Box.createVerticalStrut(8))
      }
    }
    if (projection.pendingApprovals > 0) {
      transcriptPanel.add(
        JBLabel(EngineBundle.message("acp.screen.pendingApprovals", projection.pendingApprovals)).apply {
          foreground = UIUtil.getContextHelpForeground()
          alignmentX = LEFT_ALIGNMENT
          border = JBUI.Borders.emptyTop(4)
        },
      )
    }
    transcriptPanel.revalidate()
    transcriptPanel.repaint()

    val sender = EnginePromptSender.forThread(project, threadId)
    val canSend = thread.runtimeKind == RuntimeKind.Acp &&
                  thread.status != ThreadStatus.Disconnected &&
                  sender != null
    LOG.info(
      "[$threadId] ACP screen render: canSend=$canSend, hasSender=${sender != null}, " +
      "status=${thread.status}, runtimeKind=${thread.runtimeKind}, binding=${projection.runtimeBinding}, " +
      "transcriptSize=${projection.transcript.size}",
    )
    inputArea.isEnabled = canSend
    inputArea.emptyText.text = EngineBundle.message(
      if (canSend) "acp.screen.input.hint" else "acp.screen.input.readonly",
    )
  }

  private fun emptyState(status: ThreadStatus): JComponent =
    JBLabel(EngineBundle.message("acp.screen.empty", humanize(status.name))).apply {
      foreground = UIUtil.getContextHelpForeground()
      alignmentX = LEFT_ALIGNMENT
    }

  private fun transcriptRow(entry: ThreadTranscriptEntry): JComponent = when (entry) {
    is ThreadMessage -> messageRow(entry)
    is ThreadToolCall -> toolRow(entry)
    is ThreadCommand -> entryRow(
      label = EngineBundle.message("acp.screen.row.command"),
      title = entry.title ?: entry.command ?: entry.id,
      body = entry.outputText,
      status = entry.status,
    )
    is ThreadPlan -> entryRow(
      label = EngineBundle.message("acp.screen.row.plan"),
      title = entry.title ?: entry.id,
      body = entry.items.joinToString(separator = "\n") { item -> item.title },
      status = if (entry.complete) "completed" else null,
    )
    is ThreadFileDiff -> entryRow(
      label = EngineBundle.message("acp.screen.row.diff"),
      title = entry.title ?: entry.path ?: entry.id,
      body = entry.newText.orEmpty(),
      status = entry.status,
    )
    is ThreadContextCompaction -> entryRow(
      label = EngineBundle.message("acp.screen.row.context"),
      title = entry.title ?: entry.id,
      body = entry.summary.orEmpty(),
      status = null,
    )
  }

  private fun messageRow(message: ThreadMessage): JComponent {
    return entryRow(
      label = humanize(message.role.name).uppercase(),
      title = null,
      body = message.text,
      status = if (message.complete) "completed" else null,
    )
  }

  private fun toolRow(toolCall: ThreadToolCall): JComponent {
    val body = buildList {
      toolCall.command?.takeIf { it.isNotBlank() }?.let(::add)
      toolCall.path?.takeIf { it.isNotBlank() }?.let(::add)
      toolCall.outputText.takeIf { it.isNotBlank() }?.let(::add)
      toolCall.summary?.takeIf { it.isNotBlank() }?.let(::add)
      toolCall.approval?.let { approval ->
        add(EngineBundle.message("acp.screen.approval.status", humanize(approval.status.name)))
      }
    }.joinToString(separator = "\n")
    return entryRow(
      label = EngineBundle.message("acp.screen.row.tool"),
      title = toolCall.title ?: toolCall.command ?: toolCall.kind ?: toolCall.id,
      body = body,
      status = toolCall.status,
    )
  }

  @Suppress("HardCodedStringLiteral")
  private fun entryRow(label: @NlsSafe String, title: String?, body: String, status: String?): JComponent {
    val row = JPanel(BorderLayout()).apply {
      isOpaque = false
      alignmentX = LEFT_ALIGNMENT
      maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    }
    val labelText = status?.let { EngineBundle.message("acp.screen.row.status", label, humanize(it)) } ?: label
    val roleLabel = JBLabel(labelText).apply {
      font = JBFont.small().asBold()
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.emptyBottom(2)
    }
    val textContent: @NlsSafe String = buildString {
      if (!title.isNullOrBlank()) append(title)
      if (body.isNotBlank()) {
        if (isNotEmpty()) append('\n')
        append(body)
      }
    }
    val text = JTextArea(textContent).apply {
      isEditable = false
      isFocusable = false
      lineWrap = true
      wrapStyleWord = true
      isOpaque = false
      border = null
      font = UIUtil.getLabelFont()
    }
    val body = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
      add(roleLabel)
      add(text)
    }
    row.add(body, BorderLayout.CENTER)
    return row
  }
}

/** Splits a PascalCase enum name into spaced words, e.g. `WaitingForApproval` -> `Waiting For Approval`. */
private fun humanize(name: String): String =
  name.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")

private val LOG = logger<AgentAcpThreadScreen>()
