// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.progress.impl.updateFromFlow
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.plus
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsBundle.messagePointer
import com.intellij.openapi.vcs.actions.commit.getContextCommitWorkflowHandler
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.progress.createProgressPipe
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorTextComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.progress.ProgressUIUtil
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.HtmlPanel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil
import com.intellij.vcs.VcsDisposable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.border.Border
import javax.swing.event.HyperlinkEvent
import kotlin.properties.Delegates.observable
import kotlin.properties.ReadWriteProperty

private fun JBLabel.setError(@NlsContexts.Label errorText: String) {
  text = errorText
  icon = AllIcons.General.Error
  foreground = NamedColorUtil.getErrorForeground()
  isVisible = true
}

private fun JBLabel.setWarning(@NlsContexts.Label warningText: String) {
  text = warningText
  icon = AllIcons.General.Warning
  foreground = null
  isVisible = true
}

@OptIn(FlowPreview::class)
open class CommitProgressPanel(project: Project) : CommitProgressUi, InclusionListener, DocumentListener, Disposable {
  private val scope = VcsDisposable.getInstance(project).coroutineScope.childScope("CommitProgressPanel", Dispatchers.EDT)

  private val taskInfo = CommitChecksTaskInfo()
  private val progressFlow = MutableStateFlow<InlineCommitChecksProgressIndicator?>(null)
  private var progress: InlineCommitChecksProgressIndicator? by progressFlow::value

  private val panel = NonOpaquePanel(VerticalLayout(4))
  private val scrollPane = FixedSizeScrollPanel(panel, JBDimension(400, 150))

  private val failuresPanel = FailuresPanel()
  private val label = JBLabel().apply { isVisible = false }

  private var announceCommitErrorAlarm: SingleAlarm? = null

  override var isEmptyMessage by stateFlag()
  override var isEmptyChanges by stateFlag()

  private val dumbModeFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override var isDumbMode: Boolean by dumbModeFlow::value

  init {
    scope.launch {
      dumbModeFlow.debounce(300).collect { update() }
    }
  }

  protected fun stateFlag(): ReadWriteProperty<Any?, Boolean> {
    return observable(false) { _, oldValue, newValue ->
      if (oldValue == newValue) return@observable
      update()
    }
  }

  val component: JComponent get() = scrollPane

  fun setup(commitWorkflowUi: CommitWorkflowUi, commitMessage: EditorTextComponent, border: Border) {
    panel.add(label)
    panel.add(failuresPanel)
    panel.border = border

    Disposer.register(commitWorkflowUi, this)
    commitMessage.addDocumentListener(this)
    commitWorkflowUi.addInclusionListener(this, this)

    setupProgressVisibilityDelay()
    setupProgressSpinnerTooltip()
    MyVisibilitySynchronizer().install()
  }

  @Suppress("EXPERIMENTAL_API_USAGE")
  private fun setupProgressVisibilityDelay() {
    progressFlow
      .debounce(ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS)
      .onEach { indicator ->
        if (indicator?.isRunning == true && failuresPanel.isEmpty()) {
          indicator.component.isVisible = true
          revalidatePanel()
        }
      }
      .launchIn(scope + CoroutineName("Commit checks indicator visibility"))
  }

  private fun setupProgressSpinnerTooltip() {
    val tooltip = CommitChecksProgressIndicatorTooltip({ progress }, { failuresPanel.width })
    tooltip.installOn(failuresPanel.iconLabel, this)
  }

  override fun dispose() {
    scope.cancel()
  }

  override suspend fun <T> runWithProgress(isOnlyRunCommitChecks: Boolean, action: suspend CoroutineScope.() -> T): T {
    check(progress == null) { "Commit checks indicator already created" }

    val indicator = InlineCommitChecksProgressIndicator(isOnlyRunCommitChecks)
    Disposer.register(this, indicator)
    progress = indicator

    val context = currentCoroutineContext()
    indicator.component.isVisible = false
    indicator.addStateDelegate(object : AbstractProgressIndicatorExBase() {
      override fun start() = progressStarted(indicator)
      override fun stop() = progressStopped(indicator)
      override fun cancel() = context.cancel() // cancel coroutine
    })
    indicator.start()
    try {
      return coroutineScope {
        val pipe = scope.createProgressPipe()
        val updater = launch {
          indicator.updateFromFlow(pipe.progressUpdates())
        }
        try {
          pipe.collectProgressUpdates(action)
        }
        finally {
          updater.cancel()
        }
      }
    }
    finally {
      indicator.stop()
    }
  }

  private fun progressStarted(indicator: InlineCommitChecksProgressIndicator) {
    logger<CommitProgressPanel>().assertTrue(progress == indicator)

    panel.add(indicator.component)
    addToStatusBar(indicator.statusBarDelegate)

    failuresPanel.clearFailures()
    revalidatePanel()
  }

  private fun progressStopped(indicator: InlineCommitChecksProgressIndicator) {
    logger<CommitProgressPanel>().assertTrue(progress == indicator)
    progress = null

    panel.remove(indicator.component)
    removeFromStatusBar(indicator.statusBarDelegate)
    Disposer.dispose(indicator)

    failuresPanel.endProgress()
    revalidatePanel()
  }

  private fun revalidatePanel() {
    component.parent?.let {
      it.revalidate()
      it.repaint()
    }
  }

  private fun addToStatusBar(progress: ProgressIndicatorEx) {
    val frame = WindowManagerEx.getInstanceEx().findFrameFor(null) ?: return
    val statusBar = frame.statusBar as? StatusBarEx ?: return
    statusBar.addProgress(progress, taskInfo)
  }

  private fun removeFromStatusBar(progress: ProgressIndicatorEx) {
    // `finish` tracks list of finished `TaskInfo`-s - so we pass new instance to remove from status bar
    progress.finish(taskInfo)
  }

  override fun addCommitCheckFailure(failure: CommitCheckFailure) {
    progress?.component?.isVisible = false
    failuresPanel.addFailure(failure)
    revalidatePanel()
  }

  override fun clearCommitCheckFailures() {
    failuresPanel.clearFailures()
    revalidatePanel()
  }

  override fun getCommitCheckFailures(): List<CommitCheckFailure> {
    return failuresPanel.getFailures()
  }

  override fun documentChanged(event: DocumentEvent) = clearError()

  override fun inclusionChanged() = clearError()

  protected fun update() {
    val error = buildErrorText()

    when {
      error != null -> {
        label.setError(error)
        if (AccessibleAnnouncerUtil.isAnnouncingAvailable()) {
          // Announce the error with a delay because after the commit button is pressed, it will get disabled during commit checks,
          // which will transfer the focus to the next component. Therefore, if we send the announcement immediately,
          // it may get interrupted to read the newly focused component.
          if (announceCommitErrorAlarm == null) {
            announceCommitErrorAlarm =
              SingleAlarm.singleEdtAlarm(
                task = { AccessibleAnnouncerUtil.announce(label, label.text, false) },
                delay = 500,
                parentDisposable = this,
              )
          }
          announceCommitErrorAlarm?.cancelAndRequest()
        }
      }
      isDumbMode -> label.setWarning(message("label.commit.checks.not.available.during.indexing"))
      else -> label.isVisible = false
    }
    revalidatePanel()
  }

  protected open fun clearError() {
    isEmptyMessage = false
    isEmptyChanges = false
  }

  @NlsContexts.Label
  protected open fun buildErrorText(): String? =
    when {
      isEmptyChanges && isEmptyMessage -> message("error.no.changes.no.commit.message")
      isEmptyChanges -> message("error.no.changes.to.commit")
      isEmptyMessage -> message("error.no.commit.message")
      else -> null
    }

  private inner class MyVisibilitySynchronizer : ContainerListener {
    private val childListener = object : ComponentAdapter() {
      override fun componentShown(e: ComponentEvent) {
        syncVisibility()
      }

      override fun componentHidden(e: ComponentEvent) {
        syncVisibility()
      }
    }

    fun install() {
      panel.addContainerListener(this)

      for (child in panel.components) {
        child.addComponentListener(childListener)
      }
      syncVisibility()
    }

    override fun componentAdded(e: ContainerEvent) {
      e.child.addComponentListener(childListener)
      syncVisibility()
    }

    override fun componentRemoved(e: ContainerEvent) {
      e.child.removeComponentListener(childListener)
      syncVisibility()
    }

    private fun syncVisibility() {
      val isVisible = panel.components.any { it.isVisible }
      if (scrollPane.isVisible != isVisible) {
        scrollPane.isVisible = isVisible
        revalidatePanel()
      }
    }
  }
}

sealed class CommitCheckFailure {
  object Unknown : CommitCheckFailure()

  open class WithDescription(val text: @NlsContexts.NotificationContent HtmlChunk) : CommitCheckFailure()

  class WithDetails(
    text: @NlsContexts.NotificationContent HtmlChunk,
    val viewDetailsLinkText: @NlsContexts.NotificationContent String?,
    val viewDetailsActionText: @NlsContexts.NotificationContent String,
    val viewDetails: (place: CommitProblemPlace) -> Unit,
  ) : WithDescription(text)
}

private class FailuresPanel : JBPanel<FailuresPanel>() {
  private var nextFailureId = 0
  private val failures = mutableMapOf<Int, CommitCheckFailure>()

  val iconLabel = JBLabel()
  private val description = FailuresDescriptionPanel()

  init {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    add(iconLabel)
    add(description.apply { border = empty(4, 4, 0, 0) })
    add(createCommitChecksToolbar(this).component)

    isOpaque = false
    isVisible = false
  }

  fun isEmpty(): Boolean = failures.isEmpty()

  fun clearFailures() {
    isVisible = false
    iconLabel.icon = null
    failures.clear()
    update()
  }

  fun addFailure(failure: CommitCheckFailure) {
    isVisible = true
    iconLabel.icon = AnimatedIcon.Default()
    failures[nextFailureId++] = failure
    update()
  }

  fun endProgress() {
    isVisible = failures.isNotEmpty()
    if (isVisible) iconLabel.icon = AllIcons.General.Warning
  }

  fun getFailures() = failures.values.toList()

  private fun update() {
    description.failures = failures
    description.update()
  }
}

private class FailuresDescriptionPanel : HtmlPanel() {
  private val isInitialized = true // workaround as `getBody()` is called from super constructor

  var failures: Map<Int, CommitCheckFailure> = emptyMap()

  // For `BoxLayout` to layout "commit checks toolbar" right after failures description
  override fun getMaximumSize(): Dimension {
    val size = super.getMaximumSize()
    if (isMaximumSizeSet) return size

    return Dimension(size).apply { width = preferredSize.width }
  }

  override fun getBodyFont(): Font = StartupUiUtil.labelFont
  override fun getBody(): String = if (isInitialized) buildDescription().toString() else ""
  override fun hyperlinkUpdate(e: HyperlinkEvent) = showDetails(e)

  private fun buildDescription(): HtmlChunk {
    if (failures.isEmpty()) return HtmlChunk.empty()

    val failureLinks = failures.mapNotNull {
      when (val failure = it.value) {
        is CommitCheckFailure.WithDetails -> {
          if (failure.viewDetailsLinkText != null) {
            failure.text.plus(HtmlChunk.nbsp())
              .plus(HtmlChunk.link(it.key.toString(), failure.viewDetailsLinkText))
          }
          else {
            HtmlChunk.link(it.key.toString(), failure.text)
          }
        }
        is CommitCheckFailure.WithDescription -> failure.text
        else -> null
      }
    }
    if (failureLinks.isEmpty()) return HtmlChunk.text(message("label.commit.checks.failed.unknown.reason"))
    return HtmlBuilder().appendWithSeparators(HtmlChunk.raw("<br/><br/>"), failureLinks).toFragment()
  }

  private fun showDetails(event: HyperlinkEvent) {
    if (event.eventType != HyperlinkEvent.EventType.ACTIVATED) return

    val failure = failures[event.description.toInt()] as? CommitCheckFailure.WithDetails ?: return
    failure.viewDetails(CommitProblemPlace.COMMIT_TOOLWINDOW)
  }
}

private fun createCommitChecksToolbar(target: JComponent): ActionToolbar =
  ActionManager.getInstance().createActionToolbar(
    "ChangesView.CommitChecksToolbar",
    DefaultActionGroup(RerunCommitChecksAction()),
    true
  ).apply {
    setTargetComponent(target)

    (this as? ActionToolbarImpl)?.setForceMinimumSize(true) // for `BoxLayout`
    setReservePlaceAutoPopupIcon(false)
    layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY

    component.isOpaque = false
    component.border = null
  }

private class RerunCommitChecksAction : DumbAwareAction(), TooltipDescriptionProvider {
  init {
    templatePresentation.apply {
      setText(Presentation.NULL_STRING)
      setDescription(messagePointer("tooltip.rerun.commit.checks"))

      icon = AllIcons.General.InlineRefresh
      hoveredIcon = AllIcons.General.InlineRefreshHover
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val workflowHandler = e.getContextCommitWorkflowHandler()
    val executor = workflowHandler?.getExecutor(RunCommitChecksExecutor.ID)
    e.presentation.isVisible = workflowHandler != null && executor != null
    e.presentation.isEnabled = workflowHandler != null && executor != null && workflowHandler.isExecutorEnabled(executor)
  }

  /**
   * See [com.intellij.openapi.vcs.changes.actions.CommitExecutorAction]
   */
  override fun actionPerformed(e: AnActionEvent) {
    val workflowHandler = e.getContextCommitWorkflowHandler()!!
    val executor = workflowHandler.getExecutor(RunCommitChecksExecutor.ID)!!

    workflowHandler.execute(executor)
  }
}
