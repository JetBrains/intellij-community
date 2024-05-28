// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.collaboration.async.awaitCancelling
import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.*
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.defaultButton
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.codereview.commits.CommitsBrowserComponentBuilder
import com.intellij.collaboration.ui.codereview.create.CodeReviewCreateReviewUIUtil
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.*
import com.intellij.collaboration.util.*
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.messages.MessagesService
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.*
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.*
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcsUtil.showAbove
import git4idea.ui.branch.MergeDirectionComponentFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.component.GHHtmlErrorPanel
import org.jetbrains.plugins.github.ui.component.LabeledListPanelHandle
import org.jetbrains.plugins.github.ui.component.LabeledListPanelViewModel
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
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
      InternalDecoratorImpl.preventRecursiveBackgroundUpdateOnToolwindow(this)
      layout = MigLayout(LC().gridGap("0", "0").insets("0").flowY().fill())

      add(directionSelector(vm), CC().pushX().gap(SIDE_GAPS_L, SIDE_GAPS_L, SIDE_GAPS_M, SIDE_GAPS_M))
      add(commitsPanel(vm).apply {
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
      }, CC().grow().push())
    }

    val bottomPanel = JPanel(null).apply {
      isOpaque = false
      InternalDecoratorImpl.preventRecursiveBackgroundUpdateOnToolwindow(this)
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
    withContext(Dispatchers.Main) {
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

  private fun CoroutineScope.textPanel(vm: GHPRCreateViewModel): JPanel {
    val titleField = CodeReviewCreateReviewUIUtil.createTitleEditor(vm.project, message("pull.request.create.title")).apply {
      margins = JBUI.insets(EDITOR_MARGINS, EDITOR_MARGINS, 0, EDITOR_MARGINS)
      launchNow {
        try {
          document.bindTextIn(this, vm.titleText, vm::setTitle)
          awaitCancellation()
        }
        finally {
          EditorFactory.getInstance().releaseEditor(this@apply)
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
          EditorFactory.getInstance().releaseEditor(this@apply)
        }
      }
    }
    val textPanel = JPanel(null).apply {
      isOpaque = true
      background = JBColor.lazy { EditorColorsManager.getInstance().globalScheme.defaultBackground }
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
    return textPanel
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
  private fun CoroutineScope.commitsPanel(vm: GHPRCreateViewModel): JComponent {
    val wrapper = Wrapper()
    launchNow {
      vm.commits.debounce(50).collect { commitsResult ->
        val content = commitsResult?.fold(::LoadingTextLabel,
                                          { createCommitsPanel(vm.project, it) },
                                          {
                                            val errorStatusPresenter = ErrorStatusPresenter.simple(message("pull.request.create.failed.to.load.commits"),
                                                                                                   descriptionProvider = GHHtmlErrorPanel::getLoadingErrorText)
                                            ErrorStatusPanelFactory.create(it, errorStatusPresenter)
                                          })
                      ?: SimpleHtmlPane(message("pull.request.create.select.branches"))
        wrapper.setContent(content)
      }
    }
    return wrapper
  }

  private fun createCommitsPanel(project: Project, commits: List<VcsCommitMetadata>): JComponent {
    val commitsModel = SingleValueModel(commits)
    val commitsPanel = CommitsBrowserComponentBuilder(project, commitsModel)
      .setCustomCommitRenderer(CodeReviewCreateReviewUIUtil.createCommitListCellRenderer())
      .showCommitDetails(true)
      .setEmptyCommitListText(message("pull.request.create.no.commits"))
      .create()
    return commitsPanel
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

private fun CoroutineScope.createReviewersListPanelHandle(vm: LabeledListPanelViewModel<GHPullRequestRequestedReviewer>,
                                                          avatarIconsProvider: GHAvatarIconsProvider) =
  LabeledListPanelHandle(this, vm,
                         message("pull.request.no.reviewers"),
                         message("pull.request.reviewers"),
                         { UserLabel(it, avatarIconsProvider) },
                         GHUIUtil.SelectionPresenters.PRReviewers(avatarIconsProvider))

private fun CoroutineScope.createAssigneesListPanelHandle(vm: LabeledListPanelViewModel<GHUser>,
                                                          avatarIconsProvider: GHAvatarIconsProvider) =
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
