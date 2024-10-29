// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.ex.ChangelistsLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.LocalRange
import com.intellij.openapi.vcs.ex.RangeExclusionState
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.platform.vcs.impl.icons.PlatformVcsImplIcons
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.Animator
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.commit.*
import java.awt.Color
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JComponent

private class CommitChunkPanel(private val tracker: ChangelistsLocalLineStatusTracker,
                               private val amendCommitHandler: NonModalAmendCommitHandler) : NonModalCommitPanel(tracker.project) {
  override val commitProgressUi: CommitProgressUi = object : CommitProgressPanel() {
    override var isEmptyMessage: Boolean
      get() = commitMessage.text.isBlank()
      set(_) {}
  }

  private val executorEventDispatcher = EventDispatcher.create(CommitExecutorListener::class.java)

  private val rightWrapper = Wrapper()
  private val bottomWrapper = Wrapper()

  override var editedCommit: EditedCommitPresentation? = null

  private val commitAction = object : DumbAwareAction(VcsBundle.message("commit.from.gutter.placeholder"), null, PlatformVcsImplIcons.CommitInline) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = commitMessage.text.isNotBlank()
    }

    override fun actionPerformed(e: AnActionEvent) {
      executorEventDispatcher.multicaster.executorCalled(null)
    }
  }

  private val amendCommitToggle = object : ToggleAction(VcsBundle.message("checkbox.amend") , null, PlatformVcsImplIcons.AmendInline) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      super.update(e)
      val p = e.presentation
      p.isVisible = amendCommitHandler.isAmendCommitModeSupported() == true
      p.isEnabled = isVisible && amendCommitHandler.isAmendCommitModeTogglingEnabled == true
    }

    override fun isSelected(e: AnActionEvent): Boolean = amendCommitHandler.isAmendCommitMode

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      amendCommitHandler.isAmendCommitMode = state
    }
  }.apply {
    val amendShortcut = ActionManager.getInstance().getAction("Vcs.ToggleAmendCommitMode").shortcutSet
    registerCustomShortcutSet(amendShortcut, this@CommitChunkPanel, this@CommitChunkPanel)
  }

  var forcedWidth: Int = Spec.DEFAULT_WIDTH
  val actionToolbar: ActionToolbar

  init {
    actionToolbar = buildActions()

    // layout
    rightWrapper.setContent(actionToolbar.component)

    centerPanel.removeAll()
    centerPanel
      .addToCenter(commitMessage)
      .addToRight(rightWrapper)
      .addToBottom(BorderLayoutPanel().addToRight(bottomWrapper).andTransparent())

    // ui adjustment
    centerPanel.andTransparent().withBackground(Spec.INPUT_BACKGROUND)
    resetPreferredHeight()
    andTransparent()

    val editor = commitMessage.editorField.getEditor(true)
    commitMessage.editorField.setPlaceholder(VcsBundle.message("commit.from.gutter.placeholder"))
    if (editor != null) {
      adjustEditorSettings(editor)
      centerPanel.border = CommitInputBorder(editor, this)
    }

    setupResizing(commitMessage)
    setupDocumentLengthTracker(commitMessage)
  }

  private fun setupDocumentLengthTracker(message: CommitMessage) {
    message.editorField.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        val length = event.document.textLength
        val lineCount = event.document.lineCount
        process(length, lineCount)
      }

      private fun process(length: Int, lineCount: Int) {
        if (length > Spec.INLINED_ACTIONS_TEXT_LIMIT || lineCount > 1) {
          rightWrapper.setContent(null)
          bottomWrapper.setContent(actionToolbar.component)
        } else {
          bottomWrapper.setContent(null)
          rightWrapper.setContent(actionToolbar.component)
        }
      }
    })
  }

  private fun buildActions(): ActionToolbar {
    val actionGroup = DefaultActionGroup(listOf(amendCommitToggle, Separator.create(), commitAction))
    val toolbar = ActionManager.getInstance().createActionToolbar("CommitChange", actionGroup, true)
      .apply {
        minimumButtonSize = Spec.MINIMUM_BUTTON_SIZE
      }

    return toolbar.apply {
      targetComponent = commitMessage
      component.border = JBUI.Borders.empty()
      component.isOpaque = false
    }
  }

  override fun addExecutorListener(listener: CommitExecutorListener, parent: Disposable) {
    executorEventDispatcher.addListener(listener, parent)
  }


  override fun getIncludedChanges(): List<Change> {
    val rangeStates = tracker.collectRangeStates()
    val rangeToCommit = rangeStates.first { it.excludedFromCommit == RangeExclusionState.Included }
    val changelistId = rangeToCommit.changelistId
    val changeList = ChangeListManager.getInstance(project).getChangeList(changelistId)!!

    val changes = changeList.changes.filter { it.virtualFile == tracker.virtualFile }
    return changes
  }

  private val animator = lazy {
    object : Animator("Commit Input Animation", Spec.Animation.FRAMES, Spec.Animation.DURATION, false, true) {
      override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
        val nextWidth = (Spec.MAX_WIDTH - Spec.DEFAULT_WIDTH) / totalFrames * frame + Spec.DEFAULT_WIDTH
        this@CommitChunkPanel.resizeInput(nextWidth)
      }
    }
  }

  private fun setupResizing(commitMessage: CommitMessage) {
    commitMessage.editorField.addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent?) {
        animator.value.resume()
      }
    })
  }

  private fun resizeInput(newWidth: Int) {
    forcedWidth = newWidth
    revalidate()
    repaint()
  }


  override fun getPreferredSize(): Dimension {
    val pref = super.preferredSize
    pref.height = minOf(pref.height, Spec.MAX_HEIGHT)
    pref.width = forcedWidth
    return pref
  }

  override fun activate(): Boolean = true
  override fun getIncludedUnversionedFiles(): List<FilePath> = emptyList()
  override fun getDisplayedChanges(): List<Change> = emptyList()
  override fun getDisplayedUnversionedFiles(): List<FilePath> = emptyList()

  fun resetSize() {
    resizeInput(Spec.DEFAULT_WIDTH)
    animator.value.reset()
  }
}

private class CommitChunkWorkflow(project: Project) : NonModalCommitWorkflow(project) {
  lateinit var state: ChangeListCommitState

  init {
    val vcses = ProjectLevelVcsManager.getInstance(project).allActiveVcss.toSet()
    updateVcses(vcses)
  }

  override val isDefaultCommitEnabled: Boolean
    get() = true

  override fun performCommit(sessionInfo: CommitSessionInfo) {
    val committer = LocalChangesCommitter(project, state, commitContext)
    addCommonResultHandlers(sessionInfo, committer)
    committer.addResultHandler(ShowNotificationCommitResultHandler(committer))
    committer.runCommit(VcsBundle.message("commit.changes"), false)
  }
}

private class CommitChunkWorkFlowHandler(
  val tracker: ChangelistsLocalLineStatusTracker,
  val rangeProvider: () -> LocalRange,
) : NonModalCommitWorkflowHandler<CommitChunkWorkflow, CommitChunkPanel>() {
  override val workflow: CommitChunkWorkflow = CommitChunkWorkflow(tracker.project)
  override val amendCommitHandler: NonModalAmendCommitHandler = NonModalAmendCommitHandler(this)
  override val ui: CommitChunkPanel = CommitChunkPanel(tracker, amendCommitHandler)
  override val commitPanel: CheckinProjectPanel = CommitProjectPanelAdapter(this)

  private val commitMessagePolicy = ChunkCommitMessagePolicy(project, ui.commitMessageUi)

  init {
    ui.addExecutorListener(this, this)
    workflow.addListener(this, this)
    workflow.addVcsCommitListener(object : CommitStateCleaner() {
      override fun onSuccess() {
        commitMessagePolicy.onAfterCommit()
        super.onSuccess()
      }
    }, this)
    workflow.addVcsCommitListener(PostCommitChecksRunner(), this)
    commitMessagePolicy.init()

    setupDumbModeTracking()
    setupCommitHandlersTracking()
    setupCommitChecksResultTracking()
    vcsesChanged()
  }

  override suspend fun updateWorkflow(sessionInfo: CommitSessionInfo): Boolean {
    workflow.state = getCommitState()
    return true
  }

  private fun getCommitState(): ChangeListCommitState {
    val changes = getIncludedChanges()
    val rangeStates = tracker.collectRangeStates()
    val first = rangeStates.first { it.excludedFromCommit == RangeExclusionState.Included }
    val changeList = ChangeListManager.getInstance(project).getChangeList(first.changelistId)!!
    return ChangeListCommitState(changeList, changes, ui.commitMessage.text)
  }

  override fun executorCalled(executor: CommitExecutor?) {
    tracker.excludeAllBlocks()
    tracker.setExcludedFromCommit(rangeProvider(), false)
    super.executorCalled(executor)
  }

  override fun saveCommitMessageBeforeCommit() {
    commitMessagePolicy.onBeforeCommit()
  }

  fun setPopup(popupDisposable: Disposable) {
    ui.resetSize()

    workflow.addListener(object : CommitWorkflowListener {
      override fun executionStarted() {
        Disposer.dispose(popupDisposable)
      }
    }, popupDisposable)

    Disposer.register(popupDisposable, Disposable {
      commitMessagePolicy.saveTempChunkCommitMessage(ui.commitMessageUi.text)
    })

    commitMessagePolicy.init()
  }
}

internal class CommitChunkComponent(
  tracker: ChangelistsLocalLineStatusTracker,
) {
  internal var range: LocalRange? = null

  private val workflowHandler = CommitChunkWorkFlowHandler(tracker) { range!! }

  init {
    Disposer.register(tracker.disposable, workflowHandler)
  }

  fun getCommitInput(): JComponent = workflowHandler.ui

  fun setPopup(disposable: Disposable) {
    workflowHandler.setPopup(disposable)
  }
}

private fun adjustEditorSettings(editor: EditorEx) {
  editor.scrollPane.border = JBUI.Borders.empty()
  editor.backgroundColor = Spec.INPUT_BACKGROUND
  editor.settings.isShowIntentionBulb = false
}

private object Spec {
  val DEFAULT_WIDTH: Int
    get() = JBUI.scale(255)

  val MAX_WIDTH: Int
    get() = JBUI.scale(660)

  val MAX_HEIGHT: Int
    get() = JBUI.scale(100)

  val INPUT_BACKGROUND: Color
    get() = EditorColorsManager.getInstance().globalScheme.defaultBackground

  // actions will be moved to the bottom after a message reaches this limit
  const val INLINED_ACTIONS_TEXT_LIMIT: Int = 50

  val MINIMUM_BUTTON_SIZE: Dimension = Dimension(22, 22)

  object Animation {
    const val FRAMES = 30
    const val DURATION = 200
  }
}

private class ChunkCommitMessagePolicy(
  project: Project,
  private val commitMessageUi: CommitMessageUi,
) : AbstractCommitMessagePolicy(project) {

  fun init() {
    commitMessageUi.text = getCommitMessage()
  }

  fun onBeforeCommit() {
    val commitMessage = commitMessageUi.text
    vcsConfiguration.saveCommitMessage(commitMessage)
  }

  fun onAfterCommit() {
    saveTempChunkCommitMessage("")
    commitMessageUi.text = getCommitMessage()
  }

  private fun getCommitMessage(): String {
    return vcsConfiguration.tempChunkCommitMessage
  }

  fun saveTempChunkCommitMessage(commitMessage: String) {
    vcsConfiguration.saveTempChunkCommitMessage(commitMessage)
  }
}

@Service(Service.Level.PROJECT)
internal class CommitChunkService() {
  private val components = mutableMapOf<ChangelistsLocalLineStatusTracker, CommitChunkComponent>()

  @RequiresEdt
  fun getComponent(tracker: ChangelistsLocalLineStatusTracker, range: LocalRange, popupDisposable: Disposable): CommitChunkComponent {
    return components.getOrPut(tracker) {
      createComponentForTracker(tracker)
    }.apply {
      this.range = range
      this.setPopup(popupDisposable)
    }
  }

  private fun createComponentForTracker(tracker: ChangelistsLocalLineStatusTracker): CommitChunkComponent {
    val component = CommitChunkComponent(tracker)
    Disposer.register(tracker.disposable, Disposable {
      components.remove(tracker)
    })
    return component
  }

  companion object {
    fun getInstance(project: Project) = project.service<CommitChunkService>()
  }
}

