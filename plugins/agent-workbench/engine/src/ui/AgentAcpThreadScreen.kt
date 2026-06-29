// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.ui

import com.intellij.agent.workbench.thread.view.AgentThreadViewPreferredFocusableContent
import com.intellij.agent.workbench.engine.core.RuntimeKind
import com.intellij.agent.workbench.engine.core.ThreadActionPrompt
import com.intellij.agent.workbench.engine.core.ThreadActionPromptButton
import com.intellij.agent.workbench.engine.core.ThreadEventEnvelope
import com.intellij.agent.workbench.engine.core.ThreadCommand
import com.intellij.agent.workbench.engine.core.ThreadContextCompaction
import com.intellij.agent.workbench.engine.core.ThreadFileDiff
import com.intellij.agent.workbench.engine.core.ThreadId
import com.intellij.agent.workbench.engine.core.MessageRole
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
import com.intellij.ide.setToolTipText
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBViewport
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import com.intellij.util.ui.ExtendableHTMLViewFactory
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.AdjustmentEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.JTextArea
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants
import javax.swing.plaf.basic.BasicTextUI
import javax.swing.text.DefaultCaret
import javax.swing.text.View
import kotlin.math.ceil

/**
 * Non-terminal, IDE-native screen for a Engine/ACP thread. Renders the live [ThreadProjection]
 * (status, title, transcript) from the Engine and re-renders on every projection update, so an ACP
 * (or mock/remote) session appears inside the same Agent Thread View editor tab instead of a terminal.
 */
internal class AgentAcpThreadScreen(
  private val project: Project,
  private val threadId: ThreadId,
  parent: Disposable,
) : JPanel(BorderLayout()), AgentThreadViewPreferredFocusableContent {
  private val statusLabel = JBLabel().apply { font = JBFont.label().asBold() }
  private val subtitleLabel = JBLabel().apply { foreground = UIUtil.getContextHelpForeground() }
  private var followTranscriptTail = true
  private var transcriptScrollToBottomRequested = false
  private var programmaticScrollDepth = 0
  private var lastTranscriptScrollValue = 0
  private var canUnstickTranscriptTail = true
  private var manualTranscriptTailPauseUntil = 0L
  private var renderedTranscriptEntries: List<ThreadTranscriptEntry>? = null
  private var renderedPendingApprovals = -1
  private var renderedEmptyStatus: ThreadStatus? = null
  private val renderScheduleLock = Any()
  private var projectionRenderScheduled = false
  private val transcriptPanel = ScrollableTranscriptPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    border = JBUI.Borders.empty(8)
    isOpaque = false
  }
  private val transcriptScrollPane = TranscriptScrollPane(transcriptPanel) { block ->
    runWithoutTranscriptUnsticking(block)
  }.apply { border = JBUI.Borders.empty() }
  private val inputArea = JBTextArea(3, 0).apply {
    lineWrap = true
    wrapStyleWord = true
    border = JBUI.Borders.empty(6)
    emptyText.text = EngineBundle.message("acp.screen.input.hint")
    accessibleContext.accessibleName = EngineBundle.message("acp.screen.input.accessible.name")
    addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
          e.consume()
          sendCurrentInput()
        }
      }
    })
  }

  override val preferredFocusedComponent: JComponent = inputArea

  init {
    border = JBUI.Borders.empty(4)
    transcriptPanel.scrollToBottomIfNeeded = {
      scrollTranscriptTailFromLayout()
    }
    installTranscriptScrollListeners()
    add(buildHeader(), BorderLayout.NORTH)
    add(transcriptScrollPane, BorderLayout.CENTER)
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
    inputArea.text = ""
    sender.sendPrompt(project, threadId, text)
  }

  override fun addNotify() {
    super.addNotify()
    // Opening/focusing the threadView clears its "unread" attention badge.
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
        override fun eventAppended(event: ThreadEventEnvelope) = Unit

        override fun projectionUpdated(threadId: ThreadId) {
          if (threadId != this@AgentAcpThreadScreen.threadId) return
          scheduleProjectionRender()
        }
      },
    )
  }

  private fun scheduleProjectionRender() {
    val scheduled = synchronized(renderScheduleLock) {
      if (projectionRenderScheduled) {
        false
      }
      else {
        projectionRenderScheduled = true
        true
      }
    }
    if (!scheduled) return
    ApplicationManager.getApplication().invokeLater {
      synchronized(renderScheduleLock) {
        projectionRenderScheduled = false
      }
      if (!project.isDisposed) {
        val projection = EngineProjectService.getInstance(project).projection(threadId)
        render(projection)
        // The user is looking at this thread, so any fresh agent output is already seen.
        if (isShowing) EngineUnreadTracker.getInstance(project).markRead(threadId)
      }
    }
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

    val visibleTranscriptEntries = projection.visibleTranscriptEntries()
    val shouldStickToBottom = shouldStickTranscriptToBottom(visibleTranscriptEntries)
    val emptyStatus = thread.status.takeIf { visibleTranscriptEntries.isEmpty() }
    val transcriptRenderChange = renderTranscript(projection, visibleTranscriptEntries, emptyStatus)
    if (transcriptRenderChange.changed && shouldStickToBottom) {
      scheduleScrollTranscriptToBottom()
    }

    val sender = EnginePromptSender.forThread(project, threadId)
    val canSend = thread.runtimeKind == RuntimeKind.Acp &&
                  thread.status != ThreadStatus.Disconnected &&
                  sender != null
    inputArea.isEnabled = canSend
    inputArea.emptyText.text = EngineBundle.message(
      if (canSend) "acp.screen.input.hint" else "acp.screen.input.readonly",
    )
  }

  private fun renderTranscript(
    projection: ThreadProjection,
    visibleEntries: List<ThreadTranscriptEntry>,
    emptyStatus: ThreadStatus?,
  ): TranscriptRenderChange {
    val previousEntries = renderedTranscriptEntries
    if (previousEntries == visibleEntries &&
        renderedPendingApprovals == projection.pendingApprovals &&
        renderedEmptyStatus == emptyStatus) {
      return TranscriptRenderChange(changed = false)
    }

    val requiresFullRebuild = previousEntries == null ||
                              renderedPendingApprovals != projection.pendingApprovals ||
                              renderedEmptyStatus != emptyStatus ||
                              previousEntries.isEmpty() != visibleEntries.isEmpty() ||
                              projection.pendingApprovals > 0 ||
                              renderedPendingApprovals > 0
    return if (requiresFullRebuild) {
      rebuildTranscript(projection, visibleEntries, emptyStatus)
    }
    else {
      updateTranscriptTail(previousEntries, projection, visibleEntries)
    }
  }

  private fun rebuildTranscript(
    projection: ThreadProjection,
    visibleEntries: List<ThreadTranscriptEntry>,
    emptyStatus: ThreadStatus?,
  ): TranscriptRenderChange {
    transcriptPanel.removeAll()
    if (visibleEntries.isEmpty()) {
      transcriptPanel.add(emptyState(projection.thread.status))
    }
    else {
      addTranscriptEntries(visibleEntries, startIndex = 0)
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
    rememberRenderedTranscript(visibleEntries, projection.pendingApprovals, emptyStatus)
    transcriptPanel.revalidate()
    transcriptPanel.repaint()
    return TranscriptRenderChange(changed = true)
  }

  private fun updateTranscriptTail(
    previousEntries: List<ThreadTranscriptEntry>,
    projection: ThreadProjection,
    visibleEntries: List<ThreadTranscriptEntry>,
  ): TranscriptRenderChange {
    val changedIndex = firstChangedTranscriptIndex(previousEntries, visibleEntries)
    if (changedIndex == previousEntries.lastIndex && changedIndex == visibleEntries.lastIndex) {
      val row = transcriptPanel.getComponentOrNull(transcriptComponentIndex(changedIndex)) as? TranscriptRowPanel
      if (row != null && row.updateTranscriptEntry(visibleEntries[changedIndex])) {
        rememberRenderedTranscript(visibleEntries, projection.pendingApprovals, emptyStatus = null)
        transcriptPanel.revalidate()
        transcriptPanel.repaint()
        return TranscriptRenderChange(changed = true)
      }
    }

    removeTranscriptEntriesFrom(changedIndex)
    addTranscriptEntries(visibleEntries, startIndex = changedIndex)
    rememberRenderedTranscript(visibleEntries, projection.pendingApprovals, emptyStatus = null)
    transcriptPanel.revalidate()
    transcriptPanel.repaint()
    return TranscriptRenderChange(changed = true)
  }

  private fun rememberRenderedTranscript(entries: List<ThreadTranscriptEntry>, pendingApprovals: Int, emptyStatus: ThreadStatus?) {
    renderedTranscriptEntries = entries.toList()
    renderedPendingApprovals = pendingApprovals
    renderedEmptyStatus = emptyStatus
  }

  private fun addTranscriptEntries(entries: List<ThreadTranscriptEntry>, startIndex: Int) {
    for (index in startIndex until entries.size) {
      transcriptPanel.add(transcriptRow(entries[index]))
      transcriptPanel.add(Box.createVerticalStrut(8))
    }
  }

  private fun removeTranscriptEntriesFrom(startIndex: Int) {
    val componentIndex = transcriptComponentIndex(startIndex)
    while (transcriptPanel.componentCount > componentIndex) {
      transcriptPanel.remove(componentIndex)
    }
  }

  private fun firstChangedTranscriptIndex(previousEntries: List<ThreadTranscriptEntry>, entries: List<ThreadTranscriptEntry>): Int {
    val sharedSize = minOf(previousEntries.size, entries.size)
    for (index in 0 until sharedSize) {
      if (previousEntries[index] != entries[index]) return index
    }
    return sharedSize
  }

  private fun transcriptComponentIndex(entryIndex: Int): Int = entryIndex * TRANSCRIPT_COMPONENTS_PER_ENTRY

  private fun JPanel.getComponentOrNull(index: Int): Component? =
    if (index in 0 until componentCount) getComponent(index) else null

  private fun shouldStickTranscriptToBottom(visibleEntries: List<ThreadTranscriptEntry>): Boolean {
    val previousComponents = transcriptPanel.componentCount
    val scrollState = transcriptScrollState()
    val atBottom = scrollState.isAtBottom
    val latestIsUserMessage = visibleEntries.lastOrNull().isUserMessage()
    if (latestIsUserMessage) {
      manualTranscriptTailPauseUntil = 0L
    }
    val manualTailPauseActive = isManualTranscriptTailPauseActive()
    val keepFollowingTailAcrossLayoutDrift = followTranscriptTail && !manualTailPauseActive && scrollState.isWithinTailDrift
    val shouldStickToBottom =
      previousComponents == 0 || latestIsUserMessage || (atBottom && !manualTailPauseActive) || keepFollowingTailAcrossLayoutDrift
    followTranscriptTail = shouldStickToBottom
    return shouldStickToBottom
  }

  private fun ThreadTranscriptEntry?.isUserMessage(): Boolean = this is ThreadMessage && role == MessageRole.User

  private fun ThreadProjection.visibleTranscriptEntries(): List<ThreadTranscriptEntry> = transcript.filter { entry ->
    entry !is ThreadToolCall || AgentAcpToolCallPresenter.shouldRenderInTranscript(entry)
  }

  private fun installTranscriptScrollListeners() {
    val scrollBar = transcriptScrollPane.verticalScrollBar
    scrollBar.addAdjustmentListener { event ->
      val userAdjustment = event.isUserTranscriptAdjustment()
      val canUnstickTranscriptTail = userAdjustment && !event.valueIsAdjusting && !transcriptScrollToBottomRequested
      val canUnstick = canUnstickTranscriptTail && programmaticScrollDepth == 0
      val lastScrollValueBefore = lastTranscriptScrollValue
      val scrollState = transcriptScrollState()
      if (canUnstick) {
        when {
          scrollState.value < lastScrollValueBefore -> pauseTranscriptTailForUserScroll()
          scrollState.isAtBottom && !isManualTranscriptTailPauseActive() -> followTranscriptTail = true
        }
      }
      lastTranscriptScrollValue = scrollState.value
    }
    transcriptScrollPane.addMouseWheelListener { event ->
      val wheelUp = event.wheelRotation < 0 || event.preciseWheelRotation < 0.0
      if (wheelUp) {
        pauseTranscriptTailForUserScroll()
      }
      ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed) return@invokeLater
        val afterScroll = transcriptScrollState()
        val manualTailPauseActive = isManualTranscriptTailPauseActive()
        if (afterScroll.isAtBottom && !manualTailPauseActive) {
          followTranscriptTail = true
        }
      }
    }
  }

  private fun scheduleScrollTranscriptToBottom() {
    transcriptScrollToBottomRequested = true
  }

  private fun scrollTranscriptTailFromLayout() {
    if (!followTranscriptTail && !transcriptScrollToBottomRequested) return
    scrollTranscriptToBottomIfNeeded()
  }

  private fun scrollTranscriptToBottomIfNeeded() {
    if (project.isDisposed) {
      return
    }
    if (!followTranscriptTail && !transcriptScrollToBottomRequested) {
      return
    }
    if (!isTranscriptViewportWidthStable()) {
      transcriptScrollToBottomRequested = true
      return
    }
    scrollTranscriptToBottom()
  }

  private fun scrollTranscriptToBottom() {
    val scrollBar = transcriptScrollPane.verticalScrollBar
    runWithoutTranscriptUnsticking {
      scrollBar.value = scrollBar.maximum
    }
    lastTranscriptScrollValue = scrollBar.value
    transcriptScrollToBottomRequested = false
    followTranscriptTail = true
  }

  private fun runWithoutTranscriptUnsticking(block: () -> Unit) {
    val previousCanUnstick = canUnstickTranscriptTail
    programmaticScrollDepth++
    try {
      canUnstickTranscriptTail = false
      block()
    }
    finally {
      canUnstickTranscriptTail = previousCanUnstick
      programmaticScrollDepth--
    }
  }

  private fun pauseTranscriptTailForUserScroll() {
    followTranscriptTail = false
    transcriptScrollToBottomRequested = false
    manualTranscriptTailPauseUntil = System.currentTimeMillis() + MANUAL_TAIL_PAUSE_MS
  }

  private fun isManualTranscriptTailPauseActive(): Boolean = System.currentTimeMillis() < manualTranscriptTailPauseUntil

  private fun isTranscriptViewportWidthStable(): Boolean {
    val viewport = transcriptScrollPane.viewport
    val extentWidth = viewport.extentSize.width
    if (extentWidth <= 0) return true
    val viewWidth = viewport.viewSize.width
    return viewWidth >= extentWidth - JBUI.scale(2)
  }

  private fun transcriptScrollState(): TranscriptScrollState {
    val scrollBar = transcriptScrollPane.verticalScrollBar
    return TranscriptScrollState(
      value = scrollBar.value,
      visibleAmount = scrollBar.visibleAmount,
      minimum = scrollBar.minimum,
      maximum = scrollBar.maximum,
      distanceToBottom = maxOf(0, scrollBar.maximum - (scrollBar.value + scrollBar.visibleAmount)),
      stickThreshold = JBUI.scale(STICK_TO_BOTTOM_THRESHOLD_PX),
      tailDriftThreshold = JBUI.scale(TAIL_FOLLOW_DRIFT_THRESHOLD_PX),
      valueIsAdjusting = scrollBar.model.valueIsAdjusting,
      unitIncrement = scrollBar.unitIncrement,
      blockIncrement = scrollBar.blockIncrement,
      followTail = followTranscriptTail,
      manualTailPauseActive = isManualTranscriptTailPauseActive(),
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
    is ThreadCommand -> commandRow(entry)
    is ThreadPlan -> planRow(entry)
    is ThreadFileDiff -> diffRow(entry)
    is ThreadContextCompaction -> contextRow(entry)
    is ThreadActionPrompt -> actionPromptRow(entry)
  }

  private fun messageRow(message: ThreadMessage): JComponent {
    val row = TranscriptRowPanel().apply {
      isOpaque = false
      alignmentX = LEFT_ALIGNMENT
    }
    val roleLabel = JBLabel(messageLabelText(message)).apply {
      font = JBFont.small().asBold()
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.emptyBottom(2)
    }
    val bodyTextPane = rowMarkdownPane(message.text)
    val body = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
      add(roleLabel)
      add(bodyTextPane)
    }
    row.add(body, BorderLayout.CENTER)
    row.setTranscriptUpdater { entry ->
      val updated = entry as? ThreadMessage ?: return@setTranscriptUpdater false
      if (updated.id != message.id || updated.role != message.role) return@setTranscriptUpdater false
      roleLabel.text = messageLabelText(updated)
      bodyTextPane.setMarkdownText(updated.text)
      bodyTextPane.invalidate()
      row.invalidate()
      true
    }
    return row
  }

  private fun messageLabelText(message: ThreadMessage): @NlsSafe String {
    val label = humanize(message.role.name).uppercase()
    return if (message.complete) {
      label
    }
    else {
      EngineBundle.message("acp.screen.row.status", label, EngineBundle.message("acp.screen.message.streaming"))
    }
  }

  private fun toolRow(toolCall: ThreadToolCall): JComponent {
    val row = TranscriptRowPanel().apply {
      isOpaque = false
      alignmentX = LEFT_ALIGNMENT
    }
    val body = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
    }

    fun renderToolCall(updatedToolCall: ThreadToolCall) {
      body.removeAll()
      val presentation = AgentAcpToolCallPresenter.create(
        updatedToolCall,
        textPreview = { previewText(it) },
        outputPreview = { previewText(it, OUTPUT_PREVIEW_MAX_CHARS, OUTPUT_PREVIEW_MAX_LINES) },
      )
      body.add(toolTitleTextArea(presentation.title))
      presentation.status?.let { status ->
        body.add(toolStatusLabel(status))
      }
      for ((text, monospace) in presentation.details) {
        body.add(Box.createVerticalStrut(2))
        body.add(rowTextArea(text, monospace))
      }
    }
    renderToolCall(toolCall)
    row.add(body, BorderLayout.CENTER)
    row.setTranscriptUpdater { entry ->
      val updated = entry as? ThreadToolCall ?: return@setTranscriptUpdater false
      if (updated.id != toolCall.id) return@setTranscriptUpdater false
      renderToolCall(updated)
      body.revalidate()
      body.repaint()
      row.invalidate()
      true
    }
    return row
  }

  private fun toolTitleTextArea(title: String): JTextArea = rowTextArea(title, monospace = false).apply {
    font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
  }

  private fun toolStatusLabel(status: @NlsSafe String): JBLabel = JBLabel(status).apply {
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.emptyTop(1)
    alignmentX = LEFT_ALIGNMENT
  }

  private fun commandRow(command: ThreadCommand): JComponent {
    return entryRow(
      label = EngineBundle.message("acp.screen.row.command"),
      title = command.title ?: command.command ?: command.id,
      body = previewText(command.outputText, OUTPUT_PREVIEW_MAX_CHARS, OUTPUT_PREVIEW_MAX_LINES),
      status = commandStatus(command.status, command.exitCode),
      bodyMonospace = true,
    )
  }

  private fun planRow(plan: ThreadPlan): JComponent {
    val body = plan.items.joinToString(separator = "\n") { item ->
      item.status?.takeIf { it.isNotBlank() }?.let { status ->
        EngineBundle.message("acp.screen.plan.item", humanize(status), item.title)
      } ?: item.title
    }
    return entryRow(
      label = EngineBundle.message("acp.screen.row.plan"),
      title = plan.title ?: plan.id,
      body = body,
      status = EngineBundle.message(if (plan.complete) "acp.screen.plan.completed" else "acp.screen.plan.running"),
    )
  }

  private fun diffRow(diff: ThreadFileDiff): JComponent {
    return entryRow(
      label = EngineBundle.message("acp.screen.row.diff"),
      title = diff.title ?: diff.path ?: diff.id,
      body = diff.newText?.takeIf { it.isNotBlank() }?.let { previewText(it, OUTPUT_PREVIEW_MAX_CHARS, OUTPUT_PREVIEW_MAX_LINES) }
             ?: EngineBundle.message("acp.screen.diff.empty"),
      status = displayStatus(diff.status),
      bodyMonospace = true,
    )
  }

  private fun contextRow(context: ThreadContextCompaction): JComponent {
    return entryRow(
      label = EngineBundle.message("acp.screen.row.context"),
      title = context.title ?: context.id,
      body = previewText(context.summary.orEmpty()),
      status = null,
    )
  }

  private fun actionPromptRow(prompt: ThreadActionPrompt): JComponent {
    val row = TranscriptRowPanel().apply {
      isOpaque = false
      alignmentX = LEFT_ALIGNMENT
    }
    val roleLabel = JBLabel(EngineBundle.message("acp.screen.row.authorization")).apply {
      font = JBFont.small().asBold()
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.emptyBottom(2)
    }
    val body = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
      add(roleLabel)
      add(rowTextArea(prompt.title, monospace = false))
      prompt.message?.takeIf { it.isNotBlank() }?.let { message ->
        add(Box.createVerticalStrut(2))
        add(rowTextArea(message, monospace = false))
      }
      val buttonPanel = promptButtonPanel(prompt.buttons)
      if (buttonPanel != null) {
        add(buttonPanel)
      }
    }
    row.add(body, BorderLayout.CENTER)
    return row
  }

  private fun promptButtonPanel(buttons: List<ThreadActionPromptButton>): JComponent? {
    val actionManager = ActionManager.getInstance()
    val panel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
      isOpaque = false
      alignmentX = LEFT_ALIGNMENT
      border = JBUI.Borders.emptyTop(6)
    }
    for (button in buttons) {
      val actionId = button.actionId
      val actionKind = button.actionKind
      val component = when {
        actionId != null -> {
          val action = actionManager.getAction(actionId)
          if (action == null) {
            LOG.warn("[$threadId] ACP action prompt skipped missing action: $actionId")
            null
          }
          else {
            promptButton(button) {
              val dataContext = DataContext { dataId ->
                if (CommonDataKeys.PROJECT.`is`(dataId)) project else null
              }
              val event = AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
              ActionUtil.performAction(action, event)
            }
          }
        }
        actionKind != null -> {
          promptButton(button) {
            val sender = EnginePromptSender.forThread(project, threadId)
            if (sender == null || !sender.handleActionPromptButton(project, threadId, button)) {
              LOG.warn("[$threadId] ACP action prompt skipped unhandled action kind: $actionKind")
            }
          }
        }
        else -> {
          LOG.warn("[$threadId] ACP action prompt skipped button without action: ${button.id}")
          null
        }
      }
      if (component != null) {
        if (panel.componentCount > 0) panel.add(Box.createHorizontalStrut(JBUI.scale(6)))
        panel.add(component)
      }
    }
    return panel.takeIf { it.componentCount > 0 }
  }

  private fun promptButton(button: ThreadActionPromptButton, action: () -> Unit): JButton {
    return JButton(button.text).apply {
      button.description?.let { setToolTipText(HtmlChunk.text(it)) }
      accessibleContext.accessibleName = button.text
      accessibleContext.accessibleDescription = button.description
      addActionListener { action() }
    }
  }

  private fun entryRow(
    label: @NlsSafe String,
    title: String?,
    body: @NlsSafe String,
    status: @NlsSafe String?,
    bodyMonospace: Boolean = false,
  ): JComponent = entryRow(
    label = label,
    title = title,
    sections = body.takeIf { it.isNotBlank() }?.let { listOf(RowBodySection(it, bodyMonospace)) }.orEmpty(),
    status = status,
  )

  private fun entryRow(label: @NlsSafe String, title: String?, sections: List<RowBodySection>, status: @NlsSafe String?): JComponent {
    val row = TranscriptRowPanel().apply {
      isOpaque = false
      alignmentX = LEFT_ALIGNMENT
    }
    val labelText = status?.let { EngineBundle.message("acp.screen.row.status", label, it) } ?: label
    val roleLabel = JBLabel(labelText).apply {
      font = JBFont.small().asBold()
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.emptyBottom(2)
    }
    val body = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
      add(roleLabel)
      title?.takeIf { it.isNotBlank() }?.let { add(rowTextArea(it, monospace = false)) }
      for ((text, monospace) in sections) {
        if (componentCount > 1) add(Box.createVerticalStrut(2))
        add(rowTextArea(text, monospace))
      }
    }
    row.add(body, BorderLayout.CENTER)
    return row
  }

  private fun rowTextArea(text: @NlsSafe String, monospace: Boolean): JTextArea = TranscriptTextArea(text).apply {
    isEditable = false
    isFocusable = false
    lineWrap = true
    wrapStyleWord = !monospace
    isOpaque = false
    border = null
    font = if (monospace) Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size) else UIUtil.getLabelFont()
    alignmentX = LEFT_ALIGNMENT
  }

  private fun rowMarkdownPane(text: @NlsSafe String): TranscriptMessageMarkdownPane =
    TranscriptMessageMarkdownPane(project).apply {
      setMarkdownText(text)
    }

  private fun previewText(text: String, maxChars: Int = BODY_PREVIEW_MAX_CHARS, maxLines: Int = BODY_PREVIEW_MAX_LINES): @NlsSafe String {
    var truncated = false
    val charLimited = if (text.length > maxChars) {
      truncated = true
      text.take(maxChars)
    }
    else {
      text
    }
    val lines = charLimited.lineSequence().take(maxLines + 1).toList()
    val lineLimited = if (lines.size > maxLines) {
      truncated = true
      lines.take(maxLines).joinToString(separator = "\n")
    }
    else {
      charLimited
    }
    if (!truncated) return lineLimited
    return buildString {
      append(lineLimited.trimEnd())
      if (isNotEmpty()) append('\n')
      append(EngineBundle.message("acp.screen.output.truncated"))
    }
  }

  private fun displayStatus(status: String?): @NlsSafe String? = status?.takeIf { it.isNotBlank() }?.let(::humanize)

  private fun commandStatus(status: String?, exitCode: Int?): @NlsSafe String? {
    val displayStatus = displayStatus(status)
    return when {
      displayStatus != null && exitCode != null -> EngineBundle.message("acp.screen.command.status.exitCode", displayStatus, exitCode)
      displayStatus != null -> displayStatus
      exitCode != null -> EngineBundle.message("acp.screen.command.exitCode", exitCode)
      else -> null
    }
  }

  private data class TranscriptRenderChange(
    val changed: Boolean,
  )

  private data class TranscriptScrollState(
    val value: Int,
    val visibleAmount: Int,
    val minimum: Int,
    val maximum: Int,
    val distanceToBottom: Int,
    val stickThreshold: Int,
    val tailDriftThreshold: Int,
    val valueIsAdjusting: Boolean,
    val unitIncrement: Int,
    val blockIncrement: Int,
    val followTail: Boolean,
    val manualTailPauseActive: Boolean,
  ) {
    val isAtBottom: Boolean
      get() = distanceToBottom <= stickThreshold

    val isWithinTailDrift: Boolean
      get() = distanceToBottom <= tailDriftThreshold
  }

  private data class RowBodySection(
    val text: @NlsSafe String,
    val monospace: Boolean = false,
  )
}

private class TranscriptScrollPane(
  transcriptPanel: ScrollableTranscriptPanel,
  private val runWithoutUnsticking: (() -> Unit) -> Unit,
) : JBScrollPane(
  transcriptPanel,
  VERTICAL_SCROLLBAR_AS_NEEDED,
  HORIZONTAL_SCROLLBAR_NEVER,
) {
  init {
    verticalScrollBar.putClientProperty(IGNORE_SCROLLBAR_IN_INSETS, true)
    viewport.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        transcriptPanel.invalidate()
      }
    })
  }

  override fun createViewport(): JViewport {
    return object : JBViewport() {
      override fun setViewPosition(p: Point?) {
        runWithoutUnsticking { super.setViewPosition(p) }
      }

      override fun setViewSize(newSize: Dimension?) {
        runWithoutUnsticking { super.setViewSize(newSize) }
      }

      override fun setBounds(r: Rectangle) {
        runWithoutUnsticking {
          isViewSizeSet = false
          super.setBounds(r)
        }
      }
    }
  }
}

private class ScrollableTranscriptPanel : JPanel(), Scrollable {
  var scrollToBottomIfNeeded: () -> Unit = {}

  override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

  override fun getPreferredSize(): Dimension {
    val preferred = super.getPreferredSize()
    val viewportWidth = viewportWidth()
    if (viewportWidth <= 0) return preferred
    return Dimension(viewportWidth, preferred.height)
  }

  override fun doLayout() {
    super.doLayout()
    scrollToBottomIfNeeded()
  }

  override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)

  override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
    maxOf(JBUI.scale(16), visibleRect.height - JBUI.scale(16))

  override fun getScrollableTracksViewportWidth(): Boolean = true

  override fun getScrollableTracksViewportHeight(): Boolean = false

  fun contentWidth(): Int {
    val viewportWidth = viewportWidth().takeIf { it > 0 } ?: width
    val insets = insets
    return (viewportWidth - insets.left - insets.right).coerceAtLeast(0)
  }

  private fun viewportWidth(): Int = (parent as? JViewport)?.extentSize?.width ?: 0
}

private class TranscriptRowPanel : JPanel(BorderLayout()) {
  private var transcriptUpdater: ((ThreadTranscriptEntry) -> Boolean)? = null

  fun setTranscriptUpdater(updater: (ThreadTranscriptEntry) -> Boolean) {
    transcriptUpdater = updater
  }

  fun updateTranscriptEntry(entry: ThreadTranscriptEntry): Boolean = transcriptUpdater?.invoke(entry) == true

  override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

private class TranscriptTextArea(text: @NlsSafe String) : JTextArea(text) {
  override fun getPreferredSize(): Dimension {
    val availableWidth = transcriptContentWidth()
    if (availableWidth <= 0) return super.getPreferredSize()

    val textUi = ui as? BasicTextUI ?: return Dimension(availableWidth, super.getPreferredSize().height)
    val insets = insets
    val textWidth = (availableWidth - insets.left - insets.right).coerceAtLeast(1)
    val rootView = textUi.getRootView(this)
    rootView.setSize(textWidth.toFloat(), Float.MAX_VALUE)
    val preferredHeight = ceil(rootView.getPreferredSpan(View.Y_AXIS).toDouble()).toInt() + insets.top + insets.bottom
    return Dimension(availableWidth, preferredHeight)
  }

  override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

  private fun transcriptContentWidth(): Int {
    var component: Component? = parent
    while (component != null) {
      if (component is ScrollableTranscriptPanel) return component.contentWidth()
      component = component.parent
    }
    return 0
  }
}

private class TranscriptMessageMarkdownPane(
  private val project: Project,
) : JBHtmlPane(MARKDOWN_STYLE_CONFIGURATION, MARKDOWN_PANE_CONFIGURATION) {
  private var markdownText: String = ""
  private val markdownRenderLock = Any()
  private var pendingMarkdownText: String? = null
  private var markdownRenderInProgress = false

  init {
    isOpaque = false
    isFocusable = false
    border = null
    font = UIUtil.getLabelFont()
    alignmentX = LEFT_ALIGNMENT
    caret = DefaultCaret()
    putClientProperty("caretWidth", null)
    caretPosition = 0
    (caret as? DefaultCaret)?.updatePolicy = DefaultCaret.NEVER_UPDATE
    addHyperlinkListener { event ->
      AgentAcpThreadHyperlinkHandler.handle(project, event)
    }
  }

  fun setMarkdownText(text: @NlsSafe String) {
    if (markdownText == text) return
    markdownText = text
    isVisible = text.isNotBlank()
    if (text.isBlank()) {
      synchronized(markdownRenderLock) {
        pendingMarkdownText = null
      }
      applyHtml(text, "")
      return
    }

    val shouldScheduleRender = synchronized(markdownRenderLock) {
      pendingMarkdownText = text
      if (markdownRenderInProgress) {
        false
      }
      else {
        markdownRenderInProgress = true
        true
      }
    }
    if (shouldScheduleRender) {
      ApplicationManager.getApplication().executeOnPooledThread {
        renderPendingMarkdown()
      }
    }
  }

  private fun renderPendingMarkdown() {
    while (true) {
      val textToRender = synchronized(markdownRenderLock) {
        val pending = pendingMarkdownText
        if (pending == null) {
          markdownRenderInProgress = false
          return
        }
        pendingMarkdownText = null
        pending
      }
      val html = AgentAcpThreadMessageMarkdownRenderer.renderHtmlDocument(textToRender)
      ApplicationManager.getApplication().invokeLater {
        applyHtml(textToRender, html)
      }
    }
  }

  private fun applyHtml(textToRender: String, html: String) {
    if (!project.isDisposed && markdownText == textToRender) {
      this.text = html
      isVisible = textToRender.isNotBlank()
      invalidate()
      revalidate()
      repaint()
    }
  }

  override fun getPreferredSize(): Dimension {
    val availableWidth = transcriptContentWidth()
    if (availableWidth <= 0) return super.getPreferredSize()

    val previousSize = size
    setSize(availableWidth, Short.MAX_VALUE.toInt())
    val preferredHeight = super.getPreferredSize().height
    size = previousSize
    return Dimension(availableWidth, preferredHeight)
  }

  override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

  private fun transcriptContentWidth(): Int {
    var component: Component? = parent
    while (component != null) {
      if (component is ScrollableTranscriptPanel) return component.contentWidth()
      component = component.parent
    }
    return 0
  }
}

private fun AdjustmentEvent.isUserTranscriptAdjustment(): Boolean =
  valueIsAdjusting || when (adjustmentType) {
    AdjustmentEvent.UNIT_INCREMENT,
    AdjustmentEvent.UNIT_DECREMENT,
    AdjustmentEvent.BLOCK_INCREMENT,
    AdjustmentEvent.BLOCK_DECREMENT,
      -> true
    else -> false
  }


/** Splits a PascalCase enum name into spaced words, e.g. `WaitingForApproval` -> `Waiting For Approval`. */
private fun humanize(name: String): String =
  name.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")

private const val BODY_PREVIEW_MAX_CHARS = 16 * 1024
private const val BODY_PREVIEW_MAX_LINES = 120
private const val OUTPUT_PREVIEW_MAX_CHARS = 8 * 1024
private const val OUTPUT_PREVIEW_MAX_LINES = 60
private const val STICK_TO_BOTTOM_THRESHOLD_PX = 24
private const val TAIL_FOLLOW_DRIFT_THRESHOLD_PX = 128
private const val MANUAL_TAIL_PAUSE_MS = 1500L
private const val TRANSCRIPT_COMPONENTS_PER_ENTRY = 2

private val MARKDOWN_STYLE_CONFIGURATION = JBHtmlPaneStyleConfiguration()
private val MARKDOWN_PANE_CONFIGURATION = JBHtmlPaneConfiguration {
  extensions(ExtendableHTMLViewFactory.Extensions.WORD_WRAP)
  customStyleSheet(
    """
    body {
      margin: 0;
      padding: 0;
    }
    p {
      margin-top: 0;
      margin-bottom: 4px;
    }
    pre {
      margin-top: 2px;
      margin-bottom: 4px;
    }
    ul, ol {
      margin-top: 0;
      margin-bottom: 4px;
    }
    """.trimIndent(),
  )
}

private val LOG = logger<AgentAcpThreadScreen>()
