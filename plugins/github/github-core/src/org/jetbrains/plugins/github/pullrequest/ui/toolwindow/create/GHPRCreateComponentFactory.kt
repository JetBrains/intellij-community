// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.collaboration.async.awaitCancelling
import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.extensionListFlow
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.*
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.defaultButton
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.moveToCenter
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.codereview.changes.CodeReviewChangeListComponentFactory
import com.intellij.collaboration.ui.codereview.create.CodeReviewCreateReviewUIUtil
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsCommitInfoComponentFactory
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsCommitsComponentFactory
import com.intellij.collaboration.ui.codereview.details.CommitPresentation
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.*
import com.intellij.collaboration.util.*
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.ui.messages.MessagesService
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.*
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.*
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.vcsUtil.showAbove
import git4idea.ui.branch.MergeDirectionComponentFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModel.BranchesCheckResult
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModel.CreationState
import org.jetbrains.plugins.github.ui.component.GHHtmlErrorPanel
import org.jetbrains.plugins.github.ui.component.LabeledListPanelHandle
import org.jetbrains.plugins.github.ui.component.LabeledListPanelViewModel
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import java.util.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

private const val SIDE_GAPS_L = 12
private const val SIDE_GAPS_M = 8
private const val EDITOR_MARGINS = SIDE_GAPS_L
private const val EDITORS_GAP = 8
private const val BUTTONS_GAP = 10
private const val STATUS_GAP = 8
private const val TEXT_LINK_GAP = 8

@OptIn(FlowPreview::class)
internal object GHPRCreateComponentFactory {

  fun createIn(cs: CoroutineScope, vm: GHPRCreateViewModel): JComponent = cs.create(vm)

  private fun CoroutineScope.create(vm: GHPRCreateViewModel): JComponent {
    val topPanel = JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", "0").insets("0").flowY().fill())

      add(directionSelector(vm), CC().pushX().gap(SIDE_GAPS_L, SIDE_GAPS_L, SIDE_GAPS_M, SIDE_GAPS_M))
      add(changesPanel(vm).apply {
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
      }, CC().grow().push())
    }

    val bottomPanel = JPanel(null).apply {
      isOpaque = false
      border = IdeBorderFactory.createBorder(SideBorder.TOP)

      layout = MigLayout(LC().gridGap("0", "0").insets("0").flowY().fill())

      add(textPanel(vm), CC().grow().push())
      add(metadataPanel(vm).apply {
        border = JBUI.Borders.compound(IdeBorderFactory.createBorder(SideBorder.TOP or SideBorder.BOTTOM),
                                       JBUI.Borders.empty(SIDE_GAPS_M, SIDE_GAPS_L))
      }, CC().growX().pushX())
      VerticalListPanel(10).apply {
        border = JBUI.Borders.empty(SIDE_GAPS_L - 2)
        add(statusPanel(vm))
        add(actionsPanel(vm))
      }.let {
        add(it, CC().pushX())
      }
    }

    launchNow {
      val uiCs = this
      try {
        vm.remoteBranchNameCallback = { suggestedName ->
          uiCs.async {
            getRemoteBranchName(vm, suggestedName)
          }.awaitCancelling()
        }
        awaitCancellation()
      }
      finally {
        vm.remoteBranchNameCallback = null
      }
    }

    return JBSplitter(true, 0.5f).apply {
      firstComponent = topPanel
      secondComponent = bottomPanel
      setHonorComponentsMinimumSize(true)
    }
  }

  private suspend fun getRemoteBranchName(vm: GHPRCreateViewModel, suggestedName: String): String =
    withContext(Dispatchers.EDT) {
      MessagesService.getInstance().showInputDialog(vm.project, null,
                                                    message("pull.request.create.input.remote.branch.name"),
                                                    message("pull.request.create.input.remote.branch.title"),
                                                    null, suggestedName, null, null,
                                                    message("pull.request.create.input.remote.branch.comment"))
      ?: run {
        cancel()
        awaitCancellation()
      }
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun CoroutineScope.textPanel(vm: GHPRCreateViewModel): JComponent {
    val cs = this

    val titleField = CodeReviewCreateReviewUIUtil.createTitleEditor(vm.project, message("pull.request.create.title")).apply {
      margins = JBUI.insets(EDITOR_MARGINS, EDITOR_MARGINS, 0, EDITOR_MARGINS)
      launchNow {
        try {
          document.bindTextIn(this, vm.titleText, vm::setTitle)
          awaitCancellation()
        }
        finally {
          withContext(NonCancellable) {
            EditorFactory.getInstance().releaseEditor(this@apply)
          }
        }
      }
    }
    val descriptionField = CodeReviewCreateReviewUIUtil.createDescriptionEditor(vm.project,
                                                                                message("pull.request.create.description")).apply {
      margins = JBUI.insets(0, EDITOR_MARGINS)
      launchNow {
        try {
          document.bindTextIn(this, vm.descriptionText, vm::setDescription)
          awaitCancellation()
        }
        finally {
          withContext(NonCancellable) {
            EditorFactory.getInstance().releaseEditor(this@apply)
          }
        }
      }
    }
    val textPanel = object : JPanel(null) {
      override fun addNotify() {
        super.addNotify()
        InternalDecoratorImpl.componentWithEditorBackgroundAdded(this)
      }

      override fun removeNotify() {
        super.removeNotify()
        InternalDecoratorImpl.componentWithEditorBackgroundRemoved(this)
      }
    }.apply {
      isOpaque = true
      background = JBColor.lazy { EditorColorsManager.getInstance().globalScheme.defaultBackground }
      InternalDecoratorImpl.preventRecursiveBackgroundUpdateOnToolwindow(this)
    }

    launchNow {
      vm.templateLoadingState.map { it.isInProgress }.distinctUntilChanged().collect {
        with(textPanel) {
          removeAll()
          if (it) {
            layout = SingleComponentCenteringLayout()
            add(LoadingLabel())
          }
          else {
            layout = BorderLayout(0, JBUI.scale(EDITORS_GAP))
            add(titleField.component.withMinHeightOfEditorLine(titleField), BorderLayout.NORTH)
            add(descriptionField.component.withMinHeightOfEditorLine(descriptionField, EDITOR_MARGINS), BorderLayout.CENTER)
          }
          revalidate()
          repaint()
        }
      }
    }

    return Wrapper().apply {
      bindContentIn(cs, GHPRTitleAndDescriptionGeneratorExtension.EP_NAME.extensionListFlow()) { extensions ->
        if (extensions.isEmpty()) {
          return@bindContentIn textPanel
        }

        val extension = extensions.first()

        val wrappedTextPanel = UiDataProvider.wrapComponent(textPanel) {
          it[GHPRCreateTitleAndDescriptionGenerationViewModel.DATA_KEY] = vm.titleAndDescriptionGenerationVm.value
        }

        val actionManager = ActionManager.getInstance()
        val actionGroup = actionManager.getAction("GitHub.Pull.Request.Create.Title.Actions") as ActionGroup
        val toolbar = actionManager.createActionToolbar("GHCreationTitle", actionGroup, true).apply {
          targetComponent = wrappedTextPanel
        }

        // Force an action's update with every new value for commits and template
        cs.launch {
          vm.titleAndDescriptionGenerationVm.collect {
            if (it == null) {
              toolbar.updateActionsAsync()
              return@collect
            }

            it.isGenerating.collect {
              toolbar.updateActionsAsync()
            }
          }
        }

        cs.launchNow {
          vm.titleAndDescriptionGenerationVm.flatMapLatest { it?.generationFeedbackActivity ?: flowOf() }.collect {
            extension.onGenerationDone(vm.project, descriptionField, it)
          }
        }

        CodeReviewCreateReviewUIUtil.createGenerationToolbarOverlay(wrappedTextPanel, toolbar) {
          (descriptionField as EditorEx).scrollPane.verticalScrollBar.preferredWidth
        }
      }
    }
  }

  private fun CoroutineScope.directionSelector(vm: GHPRCreateViewModel): JComponent {
    val cs = this
    val directionModel = GHPRCreateMergeDirectionModel(cs, vm)
    return MergeDirectionComponentFactory(
      directionModel,
      { model ->
        with(model) {
          val branch = baseBranch ?: return@with null
          val headRepoPath = headRepo?.repository?.repositoryPath
          val baseRepoPath = baseRepo.repository.repositoryPath
          val showOwner = headRepoPath != null && baseRepoPath != headRepoPath
          baseRepo.repository.repositoryPath.toString(showOwner) + ":" + branch.nameForRemoteOperations
        }
      },

      { model ->
        with(model) {
          val branch = headBranch ?: return@with null
          val headRepoPath = headRepo?.repository?.repositoryPath ?: return@with null
          val baseRepoPath = baseRepo.repository.repositoryPath
          val showOwner = baseRepoPath != headRepoPath
          headRepoPath.toString(showOwner) + ":" + branch.name
        }
      }
    ).create()
  }

  @OptIn(FlowPreview::class)
  private fun CoroutineScope.changesPanel(vm: GHPRCreateViewModel): JComponent {
    val wrapper = Wrapper()
    val errorStatusPresenter = ErrorStatusPresenter.simple(message("pull.request.create.failed.to.load.commits"),
                                                           descriptionProvider = GHHtmlErrorPanel::getLoadingErrorText)
    launchNow {
      vm.changesVm.debounce(50).collectScoped { changesResult ->
        val content = changesResult?.fold({ moveToCenter(LoadingTextLabel()) },
                                          { createChangesPanel(it) },
                                          { moveToCenter(ErrorStatusPanelFactory.create(it, errorStatusPresenter)) })
                      ?: SimpleHtmlPane(message("pull.request.create.select.branches")).let(CollaborationToolsUIUtil::moveToCenter)
        wrapper.setContent(content)
      }
    }
    return wrapper
  }

  private fun CoroutineScope.createChangesPanel(changesVm: GHPRCreateChangesViewModel?): JComponent {
    if (changesVm == null) {
      return HintPane(message("pull.request.does.not.contain.commits")).let(CollaborationToolsUIUtil::moveToCenter)
    }

    val cs = this
    val commits = CodeReviewDetailsCommitsComponentFactory
      .create(cs, changesVm, ::createCommitsPopupPresenter)
    val commitsDetails = CodeReviewDetailsCommitInfoComponentFactory
      .create(cs, changesVm.selectedCommit, ::createCommitsPopupPresenter, ::SimpleHtmlPane)

    val commitsPanel = VerticalListPanel(SIDE_GAPS_M).apply {
      add(commits)
      add(commitsDetails)
      border = JBUI.Borders.empty(SIDE_GAPS_M, SIDE_GAPS_L)
    }

    val scrollPane = ScrollPaneFactory.createScrollPane(null, true).apply {
      horizontalScrollBarPolicy = ScrollPaneFactory.HORIZONTAL_SCROLLBAR_NEVER
    }
    ScrollableContentBorder.setup(scrollPane, Side.TOP)

    val panel = JPanel(BorderLayout()).apply {
      add(commitsPanel, BorderLayout.NORTH)
      add(scrollPane, BorderLayout.CENTER)
    }.also {
      DataManager.registerDataProvider(it, object : EdtNoGetDataProvider {
        override fun dataSnapshot(sink: DataSink) {
          sink[CodeReviewChangeListViewModel.DATA_KEY] = changesVm.commitChangesVm.value.changeListVm.value.getOrNull()
        }
      })
    }

    val errorStatusPresenter = ErrorStatusPresenter.simple(message("pull.request.create.failed.to.load.changes"),
                                                           descriptionProvider = GHHtmlErrorPanel::getLoadingErrorText)
    cs.launchNow {
      changesVm.commitChangesVm.collectScoped { commitChangesVm ->
        commitChangesVm.changeListVm.debounce(50).collectScoped { changeListVmResult ->
          val content = changeListVmResult.fold({ moveToCenter(LoadingTextLabel()) },
                                                { createChangesPanel(it) },
                                                { moveToCenter(ErrorStatusPanelFactory.create(it, errorStatusPresenter)) })
          scrollPane.setViewportView(content)
          panel.revalidate()
          panel.repaint()
        }
      }
    }
    return panel
  }

  private fun CoroutineScope.createChangesPanel(changeListVm: CodeReviewChangeListViewModel): JComponent =
    CodeReviewChangeListComponentFactory.createIn(this, changeListVm, null,
                                                  message("pull.request.commit.does.not.contain.changes")).also {
      it.installPopupHandler(ActionManager.getInstance().getAction("Github.PullRequest.Changes.Popup") as ActionGroup)
    }

  private fun CoroutineScope.metadataPanel(vm: GHPRCreateViewModel): JComponent {
    val reviewersHandle = createReviewersListPanelHandle(vm.reviewersVm, vm.avatarIconsProvider)
    val assigneesHandle = createAssigneesListPanelHandle(vm.assigneesVm, vm.avatarIconsProvider)
    val labelsHandle = createLabelsListPanelHandle(vm.labelsVm)

    return JPanel().apply {
      isOpaque = true
      background = JBColor.lazy { EditorColorsManager.getInstance().globalScheme.defaultBackground }
      border = JBUI.Borders.empty(SIDE_GAPS_M, SIDE_GAPS_L)

      layout = MigLayout(LC()
                           .fillX()
                           .gridGap("0", "0")
                           .insets("0", "0", "0", "0"))

      fun addListPanel(handle: LabeledListPanelHandle<*>) {
        add(handle.label, CC().alignY("top").width(":${handle.preferredLabelWidth}px:"))
        add(handle.panel, CC().minWidth("0").growX().pushX().wrap())
      }

      addListPanel(reviewersHandle)
      addListPanel(assigneesHandle)
      addListPanel(labelsHandle)
    }
  }

  private fun CoroutineScope.statusPanel(vm: GHPRCreateViewModel) =
    VerticalListPanel(STATUS_GAP).apply {
      add(createBranchesCheckState(vm))
      add(createProgressState(vm))
      makeVisibleIfAnyChildIsVisible()
    }

  private fun CoroutineScope.createBranchesCheckState(vm: GHPRCreateViewModel): JComponent =
    HorizontalListPanel(TEXT_LINK_GAP).apply {
      launchNow {
        vm.branchesCheckState.debounce(50).collect { computedResult ->
          removeAll()
          computedResult?.onInProgress {
            add(LoadingLabel(message("pull.request.create.checking.branches")))
            isVisible = true
          }?.onFailure {
            add(ErrorLabel(message("pull.request.create.failed.to.check.branches")))
            add(ErrorLink(it))
            isVisible = true
          }?.onSuccess { result ->
            when (result) {
              is BranchesCheckResult.AlreadyExists -> {
                add(ErrorLabel(message("pull.request.create.already.exists")))
                add(ActionLink(message("pull.request.create.already.exists.view")) { result.open() })
                isVisible = true
              }
              is BranchesCheckResult.NoChanges -> {
                add(ErrorLabel(message("pull.request.create.no.changes",
                                       result.baseBranch.nameForRemoteOperations,
                                       result.headBranch.name)))
                isVisible = true
              }
              BranchesCheckResult.OK -> {
                isVisible = false
              }
            }
          } ?: run {
            isVisible = false
          }
          revalidate()
        }
      }
    }

  private fun CoroutineScope.createProgressState(vm: GHPRCreateViewModel): JComponent {
    return HorizontalListPanel(TEXT_LINK_GAP).apply {
      launchNow {
        vm.creationProgress.collectScoped {
          if (it == null || it == CreationState.CollectingData || it == CreationState.Created) {
            isVisible = false
            return@collectScoped
          }
          isVisible = true
          when (it) {
            CreationState.Pushing -> add(LoadingLabel(message("pull.request.create.pushing")))
            CreationState.CallingAPI -> add(LoadingLabel(message("pull.request.create.creating")))
            CreationState.SettingMetadata -> add(LoadingLabel(message("pull.request.create.setting.metadata")))
            is CreationState.Error -> {
              add(ErrorLabel(message("pull.request.create.error")))
              add(ErrorLink(it.error))
            }
            else -> Unit
          }
          revalidate()

          try {
            awaitCancellation()
          }
          finally {
            removeAll()
          }
        }
      }
    }
  }

  private fun CoroutineScope.actionsPanel(vm: GHPRCreateViewModel): JComponent {
    val cs = this
    val creationEnabledFlow = vm.branchesCheckState.combine(vm.creationProgress) { check, progress ->
      check?.getOrNull() == BranchesCheckResult.OK && (progress == null || progress is CreationState.Error)
    }

    val createAction = swingAction(message("pull.request.create.action")) {
      vm.create(false)
    }.apply {
      isEnabled = false
      bindEnabledIn(cs, creationEnabledFlow)
    }
    val createDraftAction = swingAction(message("pull.request.create.draft.action")) {
      vm.create(true)
    }.apply {
      isEnabled = false
      bindEnabledIn(cs, creationEnabledFlow)
    }
    val createButton = JBOptionButton(createAction, arrayOf(createDraftAction)).apply {
      defaultButton()
    }

    return HorizontalListPanel(BUTTONS_GAP).apply {
      add(createButton)
    }
  }
}

private fun createCommitsPopupPresenter(commit: VcsCommitMetadata) = CommitPresentation(
  titleHtml = commit.subject,
  descriptionHtml = commit.fullMessage.split("\n\n").getOrNull(1).orEmpty(),
  author = VcsUserUtil.getShortPresentation(commit.author),
  committedDate = Date(commit.authorTime)
)

private fun CoroutineScope.createReviewersListPanelHandle(
  vm: LabeledListPanelViewModel<GHPullRequestRequestedReviewer>,
  avatarIconsProvider: GHAvatarIconsProvider,
) =
  LabeledListPanelHandle(this, vm,
                         message("pull.request.no.reviewers"),
                         message("pull.request.reviewers"),
                         { UserLabel(it, avatarIconsProvider) },
                         GHUIUtil.SelectionPresenters.PRReviewers(avatarIconsProvider))

private fun CoroutineScope.createAssigneesListPanelHandle(
  vm: LabeledListPanelViewModel<GHUser>,
  avatarIconsProvider: GHAvatarIconsProvider,
) =
  LabeledListPanelHandle(this, vm,
                         message("pull.request.unassigned"),
                         message("pull.request.assignees"),
                         { UserLabel(it, avatarIconsProvider) },
                         GHUIUtil.SelectionPresenters.Users(avatarIconsProvider))

private fun CoroutineScope.createLabelsListPanelHandle(vm: LabeledListPanelViewModel<GHLabel>) =
  LabeledListPanelHandle(this, vm,
                         message("pull.request.no.labels"),
                         message("pull.request.labels"),
                         { LabelLabel(it) },
                         GHUIUtil.SelectionPresenters.Labels())

@Suppress("FunctionName")
private fun UserLabel(user: GHPullRequestRequestedReviewer, avatarIconsProvider: GHAvatarIconsProvider) =
  JLabel(user.shortName, avatarIconsProvider.getIcon(user.avatarUrl, Avatar.Sizes.BASE), SwingConstants.LEFT).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2)
  }

@Suppress("FunctionName")
private fun LabelLabel(label: GHLabel) =
  Wrapper(GHUIUtil.createIssueLabelLabel(label)).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP + 1, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP + 2, UIUtil.DEFAULT_HGAP / 2)
  }

@Suppress("FunctionName")
private fun ErrorLabel(text: @Nls String) = JLabel(text, AllIcons.Ide.FatalError, SwingConstants.LEFT).also {
  JLabelUtil.setTrimOverflow(it, true)
}

@Suppress("FunctionName")
private fun ErrorLink(error: Throwable) =
  ActionLink(message("pull.request.create.error.details")).also { link ->
    link.addActionListener {
      val errorStatusPresenter = ErrorStatusPresenter.simple(message("pull.request.create.error"),
                                                             descriptionProvider = GHHtmlErrorPanel::getLoadingErrorText)
      val errorTextPane = ErrorStatusPanelFactory.create(error, errorStatusPresenter, ErrorStatusPanelFactory.Alignment.LEFT).let {
        ScrollPaneFactory.createScrollPane(it, true)
      }.apply {
        preferredSize = JBDimension(300, 400)
      }
      JBPopupFactory.getInstance().createComponentPopupBuilder(errorTextPane, errorTextPane)
        .setResizable(true)
        .setCancelOnClickOutside(true)
        .createPopup().showAbove(link)
    }
  }

@Suppress("FunctionName")
private fun HintPane(message: @Nls String) = SimpleHtmlPane(message).apply {
  foreground = UIUtil.getContextHelpForeground()
}

private var Editor.margins
  get() = (this as? EditorEx)?.scrollPane?.viewportBorder?.getBorderInsets(scrollPane.viewport) ?: JBUI.emptyInsets()
  set(value) {
    (this as? EditorEx)?.scrollPane?.viewportBorder = JBUI.Borders.empty(value)
  }

private fun Component.withMinHeightOfEditorLine(editor: Editor, additionalGapBottom: Int = 0): JPanel {
  val restrictions = object : DimensionRestrictions {
    override fun getWidth(): Int? = null
    override fun getHeight(): Int = editor.lineHeight +
                                    JBUI.scale(additionalGapBottom) +
                                    editor.insets.run { top + bottom } +
                                    editor.margins.run { top + bottom }
  }
  return withMinSize(restrictions)
}

private fun Component.withMinSize(restrictions: DimensionRestrictions): JPanel {
  val component = this
  return JPanel(null).apply {
    isOpaque = false
    layout = SizeRestrictedSingleComponentLayout().apply {
      minSize = restrictions
    }
    add(component)
  }
}

private fun JComponent.makeVisibleIfAnyChildIsVisible(): ContainerListener {
  fun updateVisibility() {
    isVisible = components.any { it.isVisible }
  }

  val visibilityListener = object : ComponentAdapter() {
    override fun componentShown(e: ComponentEvent) = updateVisibility()
    override fun componentHidden(e: ComponentEvent) = updateVisibility()
  }

  val listener = object : ContainerListener {
    override fun componentAdded(e: ContainerEvent) {
      e.component.addComponentListener(visibilityListener)
      updateVisibility()
    }

    override fun componentRemoved(e: ContainerEvent) {
      e.component.removeComponentListener(visibilityListener)
      updateVisibility()
    }
  }
  addContainerListener(listener)
  components.forEach {
    it.addComponentListener(visibilityListener)
  }
  updateVisibility()
  return listener
}
