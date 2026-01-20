// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.util.DiffUtil
import com.intellij.ide.dnd.DnDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.wrap
import com.intellij.openapi.application.ModalityState.nonModal
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Factory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager.ChangesViewWorkflowListener
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.changes.PreviewDiffSplitterComponent
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.content.Content
import com.intellij.util.ModalityUiUtil.invokeLaterIfNeeded
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.launchOnShow
import com.intellij.vcs.changes.viewModel.ChangesViewProxy
import com.intellij.vcs.commit.*
import com.intellij.vcs.commit.CommitModeManager.Companion.subscribeOnCommitModeChange
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Predicate
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.SwingConstants

private const val CHANGES_VIEW_PREVIEW_SPLITTER_PROPORTION = "ChangesViewManager.DETAILS_SPLITTER_PROPORTION"

class ChangesViewManager internal constructor(private val project: Project, private val cs: CoroutineScope) : ChangesViewEx, Disposable {
  internal var changesView: ChangesViewProxy? = null
    private set

  @RequiresEdt
  @ApiStatus.Internal
  override fun getOrCreateCommitChangesView(): ChangesViewProxy {
    return changesView ?: run {
      val activity = StartUpMeasurer.startActivity("ChangesViewPanel initialization")
      val view = ChangesViewProxy.create(project, cs).also {
        Disposer.register(this@ChangesViewManager, it)
      }
      activity.end()
      view
    }.also {
      changesView = it
    }
  }

  private fun disposeCommitChangesView() {
    changesView?.let { Disposer.dispose(it) }
    changesView = null
  }

  override fun dispose() {
  }

  override fun scheduleRefresh(callback: Runnable) {
    changesView?.scheduleRefreshNow(callback)
  }

  override fun scheduleRefresh() {
    changesView?.scheduleDelayedRefresh()
  }

  override fun selectFile(vFile: VirtualFile?) {
    changesView?.selectFile(vFile)
  }

  override fun selectChanges(changes: List<Change>) {
    changesView?.selectChanges(changes.toList())
  }

  override fun setGrouping(groupingKey: String) {
    changesView?.setGrouping(groupingKey)
  }

  override fun resetViewImmediatelyAndRefreshLater() {
    changesView?.resetViewImmediatelyAndRefreshLater()
  }

  internal class ContentPreloader(private val project: Project) : ChangesViewContentProvider.Preloader {
    override fun preloadTabContent(content: Content) {
      ChangesViewCommitTabTitleUpdater(project, ChangesViewContentManager.LOCAL_CHANGES).init(content)

      content.putUserData(Content.TAB_DND_TARGET_KEY, MyContentDnDTarget(project, content))
    }
  }

  internal class ContentPredicate : Predicate<Project> {
    override fun test(project: Project): Boolean {
      return ProjectLevelVcsManager.getInstance(project).hasActiveVcss() &&
             !CommitModeManager.getInstance(project).getCurrentCommitMode().isLocalChangesTabHidden
    }
  }

  internal class ContentProvider(private val project: Project) : ChangesViewContentProvider {
    override fun initTabContent(content: Content) {
      val activity = StartUpMeasurer.startActivity("ChangesViewToolWindowPanel initialization")
      val viewManager = getInstance(project) as ChangesViewManager
      val changesView = viewManager.getOrCreateCommitChangesView()
      val panel = ChangesViewToolWindowPanel(project, changesView)

      fun updateCommitWorkflow() {
        val workflow = ChangesViewWorkflowManager.getInstance(project).commitWorkflowHandler
        panel.setCommitUi(workflow?.ui)
      }
      updateCommitWorkflow()
      project.getMessageBus().connect(panel)
        .subscribe(ChangesViewWorkflowManager.TOPIC, ChangesViewWorkflowListener { updateCommitWorkflow() })

      Disposer.register(panel, Disposable {
        viewManager.disposeCommitChangesView()
      })

      content.setHelpId(ChangesListView.HELP_ID)
      content.setComponent(panel) // panel disposed with content
      content.setPreferredFocusableComponent(changesView.getPreferredFocusedComponent())
      activity.end()
    }
  }

  private class MyContentDnDTarget(project: Project, content: Content) : VcsToolwindowDnDTarget(project, content) {
    override fun drop(event: DnDEvent) {
      super.drop(event)
      val attachedObject = event.getAttachedObject()
      if (attachedObject is ShelvedChangeListDragBean) {
        ShelveChangesManager.unshelveSilentlyWithDnd(
          myProject, attachedObject, null,
          !ChangesTreeDnDSupport.isCopyAction(event)
        )
      }
    }

    override fun isDropPossible(event: DnDEvent): Boolean {
      val attachedObject = event.getAttachedObject()
      if (attachedObject is ShelvedChangeListDragBean) {
        return !attachedObject.shelvedChangelists.isEmpty()
      }
      return attachedObject is ChangeListDragBean
    }
  }

  private class ChangesViewToolWindowPanel(
    private val project: Project,
    private val changesView: ChangesViewProxy,
  ) : SimpleToolWindowPanel(false, true),
      ChangesViewController,
      Disposable {
    private val vcsConfiguration: VcsConfiguration

    private val mainPanelContent: Wrapper
    private val contentPanel: BorderLayoutPanel

    private val commitPanelSplitter: ChangesViewCommitPanelSplitter
    private val editorDiffPreview: ChangesViewEditorDiffPreview
    private var splitterDiffPreview: ChangesViewSplitterDiffPreview? = null

    private val progressLabel = Wrapper()

    private var commitPanel: ChangesViewCommitPanel? = null

    private var isDisposed = false

    init {
      changesView.initPanel()

      val busConnection = project.getMessageBus().connect(this)
      vcsConfiguration = VcsConfiguration.getInstance(project)

      registerShortcuts(this)

      busConnection.subscribe(ChangeListListener.TOPIC, object : ChangeListListener {
        override fun changedFileStatusChanged() {
          val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
          updateProgressComponent(changeListManager.additionalUpdateInfo)
        }
      })

      subscribeOnCommitModeChange(busConnection, CommitModeManager.CommitModeListener { configureToolbars() })
      configureToolbars()

      commitPanelSplitter = ChangesViewCommitPanelSplitter(project)
      Disposer.register(this, commitPanelSplitter)
      commitPanelSplitter.setFirstComponent(changesView.panel)

      contentPanel = BorderLayoutPanel()
      contentPanel.addToCenter(commitPanelSplitter)
      mainPanelContent = Wrapper(contentPanel)
      editorDiffPreview = ChangesViewEditorDiffPreview(changesView, contentPanel)
      Disposer.register(this, editorDiffPreview)

      val mainPanel = Panels.simplePanel(mainPanelContent).addToBottom(progressLabel)
      setContent(mainPanel)

      mainPanel.launchOnShow("Changes refresh on changes show") {
        ChangeListManagerRefreshHelper.requestRefresh(project)
      }.cancelOnDispose(this)

      busConnection.subscribeOnVcsToolWindowLayoutChanges(Runnable { this.updatePanelLayout() })
      updatePanelLayout()
    }

    override fun dispose() {
      isDisposed = true

      if (splitterDiffPreview != null) Disposer.dispose(splitterDiffPreview!!)
      splitterDiffPreview = null
    }

    private fun updatePanelLayout() {
      if (isDisposed) return

      val isVertical = ChangesViewContentManager.isToolWindowTabVertical(project, ChangesViewContentManager.LOCAL_CHANGES)
      val hasSplitterPreview = ChangesViewContentManager.shouldHaveSplitterDiffPreview(project, isVertical)
      val isPreviewPanelShown = hasSplitterPreview && vcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN
      commitPanelSplitter.setOrientation(isPreviewPanelShown || isVertical)

      val needUpdatePreviews = hasSplitterPreview != (splitterDiffPreview != null)
      if (!needUpdatePreviews) return

      if (hasSplitterPreview) {
        splitterDiffPreview = ChangesViewSplitterDiffPreview(changesView.createDiffPreviewProcessor(false))
        DiffPreview.setPreviewVisible(splitterDiffPreview!!, vcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN)
      }
      else {
        Disposer.dispose(splitterDiffPreview!!)
        splitterDiffPreview = null
      }
    }

    private inner class ChangesViewSplitterDiffPreview(
      private val processor: ChangeViewDiffRequestProcessor,
    ) : DiffPreview, Disposable {
      private val splitterComponent = PreviewDiffSplitterComponent(processor, CHANGES_VIEW_PREVIEW_SPLITTER_PROPORTION)

      init {
        splitterComponent.setFirstComponent(contentPanel)
        mainPanelContent.setContent(splitterComponent)
      }

      override fun dispose() {
        Disposer.dispose(processor)

        if (!this@ChangesViewToolWindowPanel.isDisposed) {
          mainPanelContent.setContent(contentPanel)
        }
      }

      override fun openPreview(requestFocus: Boolean): Boolean {
        return splitterComponent.openPreview(requestFocus)
      }

      override fun closePreview() {
        splitterComponent.closePreview()
      }
    }

    override val isDiffPreviewAvailable: Boolean
      get() = splitterDiffPreview != null

    override fun toggleDiffPreview(state: Boolean) {
      val preview = splitterDiffPreview ?: editorDiffPreview
      DiffPreview.setPreviewVisible(preview, state)
      updatePanelLayout()
    }

    private fun closeEditorPreviewIfNoChanges() {
      val changeListManager = ChangeListManager.getInstance(project)
      changeListManager.invokeAfterUpdate(true) {
        if (changeListManager.allChanges.isEmpty()) {
          editorDiffPreview.closePreview()
        }
      }
    }

    fun setCommitUi(commitUi: ChangesViewCommitPanel?) {
      if (commitUi != null) {
        commitUi.registerRootComponent(this)
        commitUi.postCommitCallback = { closeEditorPreviewIfNoChanges() }
        commitPanel = commitUi
        commitPanelSplitter.setSecondComponent(commitUi.getComponent())
      }
      else {
        commitPanelSplitter.setSecondComponent(null)
        commitPanel?.postCommitCallback = null
        commitPanel = null
      }
      configureToolbars()
    }

    private fun configureToolbars() {
      val isToolbarHorizontal = CommitModeManager.isCommitToolWindowEnabled(project)
      changesView.setToolbarHorizontal(isToolbarHorizontal)
    }

    override fun getActions(originalProvider: Boolean): List<AnAction?> {
      val actionManager = ActionManager.getInstance()
      val toolbarActionGroup = actionManager.getAction("ChangesViewToolbar.Shared") as DefaultActionGroup
      return toolbarActionGroup.getChildren(actionManager).toList()
    }

    override fun uiDataSnapshot(sink: DataSink) {
      super.uiDataSnapshot(sink)
      sink[DiffDataKeys.EDITOR_TAB_DIFF_PREVIEW] = editorDiffPreview
      sink[ChangesViewController.DATA_KEY] = this

      // This makes COMMIT_WORKFLOW_HANDLER available anywhere in "Local Changes" - so commit executor actions are enabled.
      DataSink.uiDataSnapshot(sink, commitPanel)
    }

    private fun updateProgressComponent(progress: List<Supplier<JComponent?>>) {
      invokeLaterIfNeeded(nonModal(), { isDisposed }, Runnable {
        val components = progress.mapNotNull { it.get() }
        if (!components.isEmpty()) {
          val component = DiffUtil.createStackedComponents(components, DiffUtil.TITLE_GAP)
          progressLabel.setContent(FixedSizeScrollPanel(component, JBDimension(400, 100)))
        }
        else {
          progressLabel.setContent(null)
        }
      })
    }

    companion object {
      private fun registerShortcuts(component: JComponent) {
        wrap("ChangesView.Refresh").registerCustomShortcutSet(CommonShortcuts.getRerun(), component)
        wrap("ChangesView.NewChangeList").registerCustomShortcutSet(CommonShortcuts.getNew(), component)
        wrap("ChangesView.RemoveChangeList").registerCustomShortcutSet(CommonShortcuts.getDelete(), component)
        wrap(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST).registerCustomShortcutSet(CommonShortcuts.getMove(), component)
      }
    }
  }

  @get:ApiStatus.Internal
  @get:Deprecated("Use ChangesViewWorkflowManager#getCommitWorkflowHandler")
  override val commitWorkflowHandler: ChangesViewCommitWorkflowHandler?
    get() = ChangesViewWorkflowManager.getInstance(project).commitWorkflowHandler

  @Deprecated("Use ChangesViewManager.getLocalChangesToolWindowName")
  open class DisplayNameSupplier(private val project: Project) : Supplier<String?> {
    override fun get(): String = getLocalChangesToolWindowName(project)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ChangesViewI {
      return project.getService(ChangesViewI::class.java)
    }

    @JvmStatic
    fun getInstanceEx(project: Project): ChangesViewEx {
      return getInstance(project) as ChangesViewEx
    }

    @JvmStatic
    fun createTextStatusFactory(@NlsContexts.Label text: @NlsContexts.Label String, isError: Boolean): Factory<JComponent?> {
      return Factory {
        val text = StringUtil.replace(text.trim { it <= ' ' }, "\n", UIUtil.BR)
        JBLabel(text).apply {
          setCopyable(true)
          setVerticalTextPosition(SwingConstants.TOP)
          setBorder(JBUI.Borders.empty(3))
          setForeground(if (isError) JBColor.RED else UIUtil.getLabelForeground())
        }
      }
    }

    @JvmStatic
    @Nls
    fun getLocalChangesToolWindowName(project: Project): @Nls String {
      return if (CommitModeManager.isCommitToolWindowEnabled(project)) {
        VcsBundle.message("tab.title.commit")
      }
      else {
        VcsBundle.message("local.changes.tab")
      }
    }
  }
}

@ApiStatus.Internal
interface ChangesViewController {
  val isDiffPreviewAvailable: Boolean

  fun toggleDiffPreview(state: Boolean)

  companion object {
    @JvmField
    val DATA_KEY: DataKey<ChangesViewController> = DataKey.create("ChangesViewController")
  }
}
