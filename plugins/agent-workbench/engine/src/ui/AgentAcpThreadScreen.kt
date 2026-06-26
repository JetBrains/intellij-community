// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.ui

import com.intellij.agent.workbench.engine.core.RuntimeKind
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
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBViewport
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
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
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.JTextArea
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants
import javax.swing.plaf.basic.BasicTextUI
import javax.swing.text.View
import kotlin.math.ceil

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
  private var renderCounter = 0
  private var followTranscriptTail = true
  private var transcriptScrollToBottomRequested = false
  private var programmaticScrollDepth = 0
  private var lastTranscriptScrollValue = 0
  private var canUnstickTranscriptTail = true
  private var transcriptScrollInvokeScheduled = false
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

  init {
    border = JBUI.Borders.empty(4)
    transcriptPanel.scrollToBottomIfNeeded = {
      scheduleTranscriptTailScrollFromLayout()
    }
    installTranscriptScrollDiagnostics()
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
      LOG.info("[$threadId] ACP screen ignored send: input disabled")
      return
    }
    val text = inputArea.text.trim()
    if (text.isEmpty()) return
    val service = EngineProjectService.getInstance(project)
    val projectionBeforeSend = service.projection(threadId)
    LOG.info(
      "[$threadId] ACP screen send requested: textLength=${text.length}, " +
      "projection=${projectionBeforeSend.debugSummary()}, scroll=${transcriptScrollDebugState()}, layout=${transcriptLayoutDebugState()}",
    )
    val sender = EnginePromptSender.forThread(project, threadId)
    if (sender == null) {
      val projection = service.projection(threadId)
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
    val projectionAfterDispatch = service.projection(threadId)
    LOG.info(
      "[$threadId] ACP screen send dispatched: textLength=${text.length}, " +
      "projection=${projectionAfterDispatch.debugSummary()}, " +
      "scroll=${transcriptScrollDebugState()}, layout=${transcriptLayoutDebugState()}",
    )
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
        override fun eventAppended(event: ThreadEventEnvelope) {
          if (event.threadId != this@AgentAcpThreadScreen.threadId) return
          LOG.info(
            "[$threadId] ACP screen event appended: seq=${event.seq}, type=${event.type}, " +
            "source=${event.source}, visibility=${event.visibility}, payloadKeys=${event.payload.keys.sorted()}, " +
            "scroll=${transcriptScrollDebugState()}, layout=${transcriptLayoutDebugState()}",
          )
        }

        override fun projectionUpdated(threadId: ThreadId) {
          if (threadId != this@AgentAcpThreadScreen.threadId) return
          LOG.info(
            "[$threadId] ACP screen projectionUpdated received: " +
            "scroll=${transcriptScrollDebugState()}, layout=${transcriptLayoutDebugState()}",
          )
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
    LOG.info(
      "[$threadId] ACP screen projection render schedule: scheduled=$scheduled, " +
      "scroll=${transcriptScrollDebugState()}, layout=${transcriptLayoutDebugState()}",
    )
    if (!scheduled) return
    ApplicationManager.getApplication().invokeLater {
      synchronized(renderScheduleLock) {
        projectionRenderScheduled = false
      }
      if (!project.isDisposed) {
        val projection = EngineProjectService.getInstance(project).projection(threadId)
        LOG.info(
          "[$threadId] ACP screen projectionUpdated rendering: " +
          "projection=${projection.debugSummary()}, scroll=${transcriptScrollDebugState()}, layout=${transcriptLayoutDebugState()}",
        )
        render(projection)
        // The user is looking at this thread, so any fresh agent output is already seen.
        if (isShowing) EngineUnreadTracker.getInstance(project).markRead(threadId)
      }
    }
  }

  @Suppress("HardCodedStringLiteral")
  private fun render(projection: ThreadProjection) {
    val renderId = ++renderCounter
    val thread = projection.thread
    val title: @NlsSafe String = thread.title.ifBlank { threadId.value }
    statusLabel.text = title
    subtitleLabel.text = EngineBundle.message(
      "acp.screen.subtitle",
      thread.runtimeKind.name,
      humanize(thread.status.name),
    )

    val stickDecision = transcriptStickDecision(projection)
    LOG.info(
      "[$threadId] ACP screen render#$renderId start: " +
      "transcriptSize=${projection.transcript.size}, pendingApprovals=${projection.pendingApprovals}, " +
      "previousComponents=${transcriptPanel.componentCount}, latest=${projection.transcript.lastOrNull().debugSummary()}, " +
      "stick=${stickDecision.shouldStickToBottom}, stickReason=${stickDecision.reason}, " +
      "followTailBefore=${stickDecision.followTailBefore}, followTailAfter=${stickDecision.followTailAfter}, " +
      "beforeScroll=${stickDecision.scrollState}, layout=${transcriptLayoutDebugState()}",
    )
    val emptyStatus = thread.status.takeIf { projection.transcript.isEmpty() }
    val transcriptRenderChange = renderTranscript(projection, emptyStatus, renderId)
    if (transcriptRenderChange.changed && stickDecision.shouldStickToBottom) {
      scheduleScrollTranscriptToBottom(renderId, stickDecision.reason)
    }
    else if (transcriptRenderChange.changed) {
      LOG.info(
        "[$threadId] ACP screen render#$renderId preserving scroll: " +
        "reason=${stickDecision.reason}, scroll=${transcriptScrollDebugState()}, layout=${transcriptLayoutDebugState()}",
      )
    }

    val sender = EnginePromptSender.forThread(project, threadId)
    val canSend = thread.runtimeKind == RuntimeKind.Acp &&
                  thread.status != ThreadStatus.Disconnected &&
                  sender != null
    LOG.info(
      "[$threadId] ACP screen render#$renderId input: canSend=$canSend, hasSender=${sender != null}, " +
      "status=${thread.status}, runtimeKind=${thread.runtimeKind}, binding=${projection.runtimeBinding}, " +
      "transcriptSize=${projection.transcript.size}",
    )
    inputArea.isEnabled = canSend
    inputArea.emptyText.text = EngineBundle.message(
      if (canSend) "acp.screen.input.hint" else "acp.screen.input.readonly",
    )
  }

  private fun renderTranscript(projection: ThreadProjection, emptyStatus: ThreadStatus?, renderId: Int): TranscriptRenderChange {
    val previousEntries = renderedTranscriptEntries
    if (previousEntries == projection.transcript &&
        renderedPendingApprovals == projection.pendingApprovals &&
        renderedEmptyStatus == emptyStatus) {
      LOG.info(
        "[$threadId] ACP screen render#$renderId transcript unchanged: " +
        "components=${transcriptPanel.componentCount}, scroll=${transcriptScrollDebugState()}, layout=${transcriptLayoutDebugState()}",
      )
      return TranscriptRenderChange(changed = false)
    }

    val requiresFullRebuild = previousEntries == null ||
                              renderedPendingApprovals != projection.pendingApprovals ||
                              renderedEmptyStatus != emptyStatus ||
                              previousEntries.isEmpty() != projection.transcript.isEmpty() ||
                              projection.pendingApprovals > 0 ||
                              renderedPendingApprovals > 0
    return if (requiresFullRebuild) {
      rebuildTranscript(projection, emptyStatus, renderId)
    }
    else {
      updateTranscriptTail(previousEntries, projection, renderId)
    }
  }

  private fun rebuildTranscript(projection: ThreadProjection, emptyStatus: ThreadStatus?, renderId: Int): TranscriptRenderChange {
    transcriptPanel.removeAll()
    if (projection.transcript.isEmpty()) {
      transcriptPanel.add(emptyState(projection.thread.status))
    }
    else {
      addTranscriptEntries(projection.transcript, startIndex = 0)
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
    rememberRenderedTranscript(projection, emptyStatus)
    transcriptPanel.revalidate()
    transcriptPanel.repaint()
    LOG.info(
      "[$threadId] ACP screen render#$renderId rows rebuilt: " +
      "components=${transcriptPanel.componentCount}, afterRevalidateScroll=${transcriptScrollDebugState()}, " +
      "layout=${transcriptLayoutDebugState()}",
    )
    return TranscriptRenderChange(changed = true)
  }

  private fun updateTranscriptTail(
    previousEntries: List<ThreadTranscriptEntry>,
    projection: ThreadProjection,
    renderId: Int,
  ): TranscriptRenderChange {
    val changedIndex = firstChangedTranscriptIndex(previousEntries, projection.transcript)
    if (changedIndex == previousEntries.lastIndex && changedIndex == projection.transcript.lastIndex) {
      val row = transcriptPanel.getComponentOrNull(transcriptComponentIndex(changedIndex)) as? TranscriptRowPanel
      if (row != null && row.updateTranscriptEntry(projection.transcript[changedIndex])) {
        rememberRenderedTranscript(projection, emptyStatus = null)
        transcriptPanel.revalidate()
        transcriptPanel.repaint()
        LOG.info(
          "[$threadId] ACP screen render#$renderId row updated: index=$changedIndex, " +
          "components=${transcriptPanel.componentCount}, afterUpdateScroll=${transcriptScrollDebugState()}, " +
          "layout=${transcriptLayoutDebugState()}",
        )
        return TranscriptRenderChange(changed = true)
      }
    }

    removeTranscriptEntriesFrom(changedIndex)
    addTranscriptEntries(projection.transcript, startIndex = changedIndex)
    rememberRenderedTranscript(projection, emptyStatus = null)
    transcriptPanel.revalidate()
    transcriptPanel.repaint()
    LOG.info(
      "[$threadId] ACP screen render#$renderId tail rebuilt: fromIndex=$changedIndex, " +
      "components=${transcriptPanel.componentCount}, afterRevalidateScroll=${transcriptScrollDebugState()}, " +
      "layout=${transcriptLayoutDebugState()}",
    )
    return TranscriptRenderChange(changed = true)
  }

  private fun rememberRenderedTranscript(projection: ThreadProjection, emptyStatus: ThreadStatus?) {
    renderedTranscriptEntries = projection.transcript.toList()
    renderedPendingApprovals = projection.pendingApprovals
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

  private fun transcriptStickDecision(projection: ThreadProjection): TranscriptStickDecision {
    val previousComponents = transcriptPanel.componentCount
    val scrollState = transcriptScrollState()
    val atBottom = scrollState.isAtBottom
    val latestIsUserMessage = projection.transcript.lastOrNull().isUserMessage()
    if (latestIsUserMessage) {
      manualTranscriptTailPauseUntil = 0L
    }
    val manualTailPauseActive = isManualTranscriptTailPauseActive()
    val followTailBefore = followTranscriptTail
    val keepFollowingTailAcrossLayoutDrift = followTailBefore && !manualTailPauseActive && scrollState.isWithinTailDrift
    val shouldStickToBottom = previousComponents == 0 || latestIsUserMessage || (atBottom && !manualTailPauseActive) || keepFollowingTailAcrossLayoutDrift
    val reason = when {
      previousComponents == 0 -> "initial-or-empty-panel"
      latestIsUserMessage -> "latest-entry-is-user-message"
      atBottom && !manualTailPauseActive -> "viewport-at-bottom"
      atBottom -> "manual-tail-pause-at-bottom"
      keepFollowingTailAcrossLayoutDrift -> "tail-follow-layout-drift"
      else -> "preserve-manual-scroll"
    }
    followTranscriptTail = shouldStickToBottom
    return TranscriptStickDecision(
      shouldStickToBottom = shouldStickToBottom,
      reason = reason,
      scrollState = scrollState.debugString(),
      followTailBefore = followTailBefore,
      followTailAfter = followTranscriptTail,
    )
  }

  private fun ThreadTranscriptEntry?.isUserMessage(): Boolean = this is ThreadMessage && role == MessageRole.User

  private fun installTranscriptScrollDiagnostics() {
    val scrollBar = transcriptScrollPane.verticalScrollBar
    scrollBar.addAdjustmentListener { event ->
      val followTailBefore = followTranscriptTail
      val userAdjustment = event.isUserTranscriptAdjustment()
      val canUnstick = canUnstickTranscriptTail && programmaticScrollDepth == 0
      val lastScrollValueBefore = lastTranscriptScrollValue
      val requestedBefore = transcriptScrollToBottomRequested
      val scrollState = transcriptScrollState()
      if (canUnstick) {
        when {
          scrollState.value < lastScrollValueBefore -> pauseTranscriptTailForUserScroll()
          scrollState.isAtBottom && !isManualTranscriptTailPauseActive() -> followTranscriptTail = true
        }
      }
      lastTranscriptScrollValue = scrollState.value
      LOG.info(
        "[$threadId] ACP screen scrollbar adjusted: type=${event.adjustmentType.debugAdjustmentType()}, " +
        "eventValue=${event.value}, valueIsAdjusting=${event.valueIsAdjusting}, " +
        "programmatic=${programmaticScrollDepth > 0}, canUnstick=$canUnstick, userAdjustment=$userAdjustment, " +
        "lastValueBefore=$lastScrollValueBefore, " +
        "followTailBefore=$followTailBefore, followTailAfter=$followTranscriptTail, " +
        "requestedBefore=$requestedBefore, requestedAfter=$transcriptScrollToBottomRequested, " +
        "manualTailPauseAfter=${isManualTranscriptTailPauseActive()}, " +
        "scroll=${scrollState.debugString()}, layout=${transcriptLayoutDebugState()}",
      )
    }
    transcriptScrollPane.addMouseWheelListener { event ->
      val followTailBefore = followTranscriptTail
      val beforeScroll = transcriptScrollState()
      val wheelUp = event.wheelRotation < 0 || event.preciseWheelRotation < 0.0
      val requestedBefore = transcriptScrollToBottomRequested
      if (wheelUp) {
        pauseTranscriptTailForUserScroll()
      }
      LOG.info(
        "[$threadId] ACP screen mouse wheel: rotation=${event.wheelRotation}, " +
        "preciseRotation=${event.preciseWheelRotation}, units=${event.unitsToScroll}, " +
        "wheelUp=$wheelUp, requestedBefore=$requestedBefore, requestedAfter=$transcriptScrollToBottomRequested, " +
        "manualTailPauseAfter=${isManualTranscriptTailPauseActive()}, " +
        "followTailBefore=$followTailBefore, followTailAfter=$followTranscriptTail, " +
        "beforeScroll=${beforeScroll.debugString()}, layout=${transcriptLayoutDebugState()}",
      )
      ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed) return@invokeLater
        val followTailAfterWheelBefore = followTranscriptTail
        val afterScroll = transcriptScrollState()
        val manualTailPauseActive = isManualTranscriptTailPauseActive()
        if (afterScroll.isAtBottom && !manualTailPauseActive) {
          followTranscriptTail = true
        }
        LOG.info(
          "[$threadId] ACP screen mouse wheel applied: " +
          "manualTailPauseActive=$manualTailPauseActive, " +
          "followTailBefore=$followTailAfterWheelBefore, followTailAfter=$followTranscriptTail, " +
          "afterScroll=${afterScroll.debugString()}, layout=${transcriptLayoutDebugState()}",
        )
      }
    }
  }

  private fun scheduleScrollTranscriptToBottom(renderId: Int, reason: String) {
    val requestedBefore = transcriptScrollToBottomRequested
    transcriptScrollToBottomRequested = true
    val scheduled = scheduleTranscriptTailScroll(renderId, reason)
    LOG.info(
      "[$threadId] ACP screen render#$renderId schedule scroll-to-bottom: " +
      "reason=$reason, requestedBefore=$requestedBefore, requestedAfter=true, scheduled=$scheduled, " +
      "scheduledScroll=${transcriptScrollDebugState()}, layout=${transcriptLayoutDebugState()}",
    )
  }

  private fun scheduleTranscriptTailScroll(renderId: Int?, reason: String): Boolean {
    if (transcriptScrollInvokeScheduled) return false
    transcriptScrollInvokeScheduled = true
    ApplicationManager.getApplication().invokeLater {
      transcriptScrollInvokeScheduled = false
      scrollTranscriptToBottomIfNeeded(renderId, reason)
    }
    return true
  }

  private fun scheduleTranscriptTailScrollFromLayout() {
    if (!followTranscriptTail && !transcriptScrollToBottomRequested) return
    val scheduled = scheduleTranscriptTailScroll(renderId = null, reason = "transcript-panel-layout")
    if (scheduled) {
      LOG.info(
        "[$threadId] ACP screen layout schedule scroll-to-bottom: " +
        "scroll=${transcriptScrollDebugState()}, layout=${transcriptLayoutDebugState()}",
      )
    }
  }

  private fun scrollTranscriptToBottomIfNeeded(renderId: Int?, reason: String) {
    if (project.isDisposed) {
      LOG.info("[$threadId] ACP screen ${renderDebugLabel(renderId)} skip scroll-to-bottom: trigger=scheduled, project disposed")
      return
    }
    if (!followTranscriptTail && !transcriptScrollToBottomRequested) {
      LOG.info(
        "[$threadId] ACP screen ${renderDebugLabel(renderId)} skip scroll-to-bottom: " +
        "trigger=scheduled, followTail=false, requested=false, reason=$reason, " +
        "scroll=${transcriptScrollDebugState()}, layout=${transcriptLayoutDebugState()}",
      )
      return
    }
    if (!isTranscriptViewportWidthStable()) {
      transcriptScrollToBottomRequested = true
      val scheduled = scheduleTranscriptTailScroll(renderId, reason)
      LOG.info(
        "[$threadId] ACP screen ${renderDebugLabel(renderId)} defer scroll-to-bottom: " +
        "trigger=scheduled, reason=$reason, scheduled=$scheduled, " +
        "scroll=${transcriptScrollDebugState()}, layout=${transcriptLayoutDebugState()}",
      )
      return
    }
    scrollTranscriptToBottom(renderId, reason)
  }

  private fun scrollTranscriptToBottom(renderId: Int?, reason: String) {
    val scrollBar = transcriptScrollPane.verticalScrollBar
    val beforeScroll = transcriptScrollDebugState()
    val modelTarget = (scrollBar.maximum - scrollBar.visibleAmount).coerceAtLeast(scrollBar.minimum)
    runWithoutTranscriptUnsticking {
      scrollBar.value = scrollBar.maximum
    }
    lastTranscriptScrollValue = scrollBar.value
    transcriptScrollToBottomRequested = false
    followTranscriptTail = true
    LOG.info(
      "[$threadId] ACP screen ${renderDebugLabel(renderId)} scroll-to-bottom applied: " +
      "trigger=scheduled, reason=$reason, modelTarget=$modelTarget, " +
      "lastContentBounds=${transcriptPanel.lastContentComponentBoundsDebugString()}, beforeScroll=$beforeScroll, " +
      "afterScroll=${transcriptScrollDebugState()}, layout=${transcriptLayoutDebugState()}",
    )
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

  private fun renderDebugLabel(renderId: Int?): String = renderId?.let { "render#$it" } ?: "layout"

  private fun isTranscriptViewportWidthStable(): Boolean {
    val viewport = transcriptScrollPane.viewport
    val extentWidth = viewport.extentSize.width
    if (extentWidth <= 0) return true
    val viewWidth = viewport.viewSize.width
    return viewWidth >= extentWidth - JBUI.scale(2)
  }

  private fun transcriptScrollDebugState(): String {
    return transcriptScrollState().debugString()
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

  private fun transcriptLayoutDebugState(): String =
    "panelSize=${transcriptPanel.size.debugString()}, panelPreferred=${transcriptPanel.preferredSize.debugString()}, " +
    "viewportExtent=${transcriptScrollPane.viewport.extentSize.debugString()}, " +
    "viewportViewSize=${transcriptScrollPane.viewport.viewSize.debugString()}, " +
    "viewportViewPosition=${transcriptScrollPane.viewport.viewPosition.debugString()}, " +
    "visibleRect=${transcriptPanel.visibleRect.debugString()}, " +
    "lastComponentBounds=${transcriptPanel.lastComponentBoundsDebugString()}, " +
    "lastContentBounds=${transcriptPanel.lastContentComponentBoundsDebugString()}, showing=$isShowing"

  private fun ThreadTranscriptEntry?.debugSummary(): String = when (this) {
    null -> "none"
    is ThreadMessage -> "message(id=$id, role=$role, complete=$complete, textLength=${text.length}, updatedAt=$updatedAt)"
    is ThreadToolCall -> "tool(id=$id, status=$status, complete=$complete, outputLength=${outputText.length}, updatedAt=$updatedAt)"
    is ThreadCommand -> "command(id=$id, status=$status, exitCode=$exitCode, complete=$complete, outputLength=${outputText.length}, updatedAt=$updatedAt)"
    is ThreadPlan -> "plan(id=$id, complete=$complete, items=${items.size}, updatedAt=$updatedAt)"
    is ThreadFileDiff -> "diff(id=$id, status=$status, newTextLength=${newText?.length ?: 0}, updatedAt=$updatedAt)"
    is ThreadContextCompaction -> "context(id=$id, summaryLength=${summary?.length ?: 0}, updatedAt=$updatedAt)"
  }

  private fun ThreadProjection.debugSummary(): String =
    "status=${thread.status}, runtimeKind=${thread.runtimeKind}, transcriptSize=${transcript.size}, " +
    "pendingApprovals=$pendingApprovals, lastSeq=$lastSeq, latest=${transcript.lastOrNull().debugSummary()}, " +
    "binding=${runtimeBinding != null}"

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
    val bodyText = previewText(message.text)
    val bodyTextArea = rowTextArea(bodyText, monospace = false).apply {
      isVisible = bodyText.isNotBlank()
    }
    val body = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
      add(roleLabel)
      add(bodyTextArea)
    }
    row.add(body, BorderLayout.CENTER)
    row.setTranscriptUpdater { entry ->
      val updated = entry as? ThreadMessage ?: return@setTranscriptUpdater false
      if (updated.id != message.id || updated.role != message.role) return@setTranscriptUpdater false
      val updatedBodyText = previewText(updated.text)
      roleLabel.text = messageLabelText(updated)
      if (bodyTextArea.text != updatedBodyText) {
        bodyTextArea.text = updatedBodyText
      }
      bodyTextArea.isVisible = updatedBodyText.isNotBlank()
      bodyTextArea.invalidate()
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
    val sections = buildList {
      toolCall.command?.takeIf { it.isNotBlank() }?.let { command ->
        add(RowBodySection(EngineBundle.message("acp.screen.section.command", command)))
      }
      toolCall.path?.takeIf { it.isNotBlank() }?.let { path ->
        add(RowBodySection(EngineBundle.message("acp.screen.section.path", path)))
      }
      toolCall.outputText.takeIf { it.isNotBlank() }?.let { output ->
        add(RowBodySection(EngineBundle.message("acp.screen.section.output",
                                                previewText(output, OUTPUT_PREVIEW_MAX_CHARS, OUTPUT_PREVIEW_MAX_LINES)), true))
      }
      toolCall.summary?.takeIf { it.isNotBlank() }?.let { summary ->
        add(RowBodySection(EngineBundle.message("acp.screen.section.summary", previewText(summary))))
      }
      toolCall.approval?.let { approval ->
        add(RowBodySection(EngineBundle.message("acp.screen.approval.status", humanize(approval.status.name))))
      }
    }
    return entryRow(
      label = EngineBundle.message("acp.screen.row.tool"),
      title = toolCall.title ?: toolCall.command ?: toolCall.kind ?: toolCall.id,
      sections = sections,
      status = displayStatus(toolCall.status),
    )
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

  private fun Dimension.debugString(): String = "${width}x${height}"

  private fun Point.debugString(): String = "${x},${y}"

  private fun Rectangle.debugString(): String = "${x},${y} ${width}x${height}"

  private fun JPanel.lastComponentBoundsDebugString(): String {
    val lastComponent = components.lastOrNull() ?: return "none"
    return lastComponent.bounds.debugString()
  }

  private fun JPanel.lastContentComponentBoundsDebugString(): String =
    lastContentComponent()?.bounds?.debugString() ?: "none"

  private fun JPanel.lastContentComponent(): Component? {
    for (index in componentCount - 1 downTo 0) {
      val component = getComponent(index)
      if (component !is Box.Filler) return component
    }
    return null
  }

  private data class TranscriptStickDecision(
    val shouldStickToBottom: Boolean,
    val reason: String,
    val scrollState: String,
    val followTailBefore: Boolean,
    val followTailAfter: Boolean,
  )

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

    fun debugString(): String =
      "value=$value, visible=$visibleAmount, min=$minimum, max=$maximum, " +
      "distanceToBottom=$distanceToBottom, threshold=$stickThreshold, " +
      "tailDriftThreshold=$tailDriftThreshold, valueIsAdjusting=$valueIsAdjusting, " +
      "unitIncrement=$unitIncrement, blockIncrement=$blockIncrement, " +
      "followTail=$followTail, manualTailPauseActive=$manualTailPauseActive"
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

private fun AdjustmentEvent.isUserTranscriptAdjustment(): Boolean =
  valueIsAdjusting || when (adjustmentType) {
    AdjustmentEvent.UNIT_INCREMENT,
    AdjustmentEvent.UNIT_DECREMENT,
    AdjustmentEvent.BLOCK_INCREMENT,
    AdjustmentEvent.BLOCK_DECREMENT,
      -> true
    else -> false
  }

private fun Int.debugAdjustmentType(): String = when (this) {
  AdjustmentEvent.UNIT_INCREMENT -> "UNIT_INCREMENT"
  AdjustmentEvent.UNIT_DECREMENT -> "UNIT_DECREMENT"
  AdjustmentEvent.BLOCK_INCREMENT -> "BLOCK_INCREMENT"
  AdjustmentEvent.BLOCK_DECREMENT -> "BLOCK_DECREMENT"
  AdjustmentEvent.TRACK -> "TRACK"
  else -> "UNKNOWN($this)"
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

private val LOG = logger<AgentAcpThreadScreen>()
