// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.CommonBundle
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.ListenableProgressIndicator
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.ui.*
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import git4idea.GitLocalBranch
import git4idea.GitPushUtil
import git4idea.GitPushUtil.findOrPushRemoteBranch
import git4idea.GitPushUtil.findPushTarget
import git4idea.GitRemoteBranch
import git4idea.ui.branch.MergeDirectionComponentFactory
import git4idea.ui.branch.MergeDirectionModel
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHIOExecutorLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHSimpleLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRMetadataPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabComponentController
import org.jetbrains.plugins.github.ui.util.DisableableDocument
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.util.CollectionDelta
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import java.awt.Component
import java.awt.Container
import java.awt.event.ActionEvent
import java.util.concurrent.CompletableFuture
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.text.Document

internal class GHPRCreateInfoComponentFactory(private val project: Project,
                                              private val settings: GithubPullRequestsProjectUISettings,
                                              private val dataContext: GHPRDataContext,
                                              private val viewController: GHPRToolWindowTabComponentController) {

  fun create(directionModel: MergeDirectionModel<GHGitRepositoryMapping>,
             titleDocument: Document,
             descriptionDocument: DisableableDocument,
             metadataModel: GHPRCreateMetadataModel,
             commitsCountModel: SingleValueModel<Int?>,
             existenceCheckLoadingModel: GHIOExecutorLoadingModel<GHPRIdentifier?>,
             createLoadingModel: GHCompletableFutureLoadingModel<GHPullRequestShort>): JComponent {

    val progressIndicator = ListenableProgressIndicator()
    val existenceCheckProgressIndicator = ListenableProgressIndicator()
    val createAction = CreateAction(directionModel, titleDocument, descriptionDocument, metadataModel, false, createLoadingModel,
                                    progressIndicator, GithubBundle.message("pull.request.create.action"))
    val createDraftAction = CreateAction(directionModel, titleDocument, descriptionDocument, metadataModel, true, createLoadingModel,
                                         progressIndicator, GithubBundle.message("pull.request.create.draft.action"))
    val cancelAction = object : AbstractAction(CommonBundle.getCancelButtonText()) {
      override fun actionPerformed(e: ActionEvent?) {
        val discard = Messages.showYesNoDialog(project,
                                               GithubBundle.message("pull.request.create.discard.message"),
                                               GithubBundle.message("pull.request.create.discard.title"),
                                               GithubBundle.message("pull.request.create.discard.approve"),
                                               CommonBundle.getCancelButtonText(), Messages.getWarningIcon())
        if (discard == Messages.NO) return

        progressIndicator.cancel()
        viewController.viewList()
        viewController.resetNewPullRequestView()
        createLoadingModel.future = null
        existenceCheckLoadingModel.reset()
      }
    }
    InfoController(directionModel, existenceCheckLoadingModel, existenceCheckProgressIndicator, createAction, createDraftAction)

    val directionSelector = MergeDirectionComponentFactory(
      directionModel,
      { model ->
        with(model) {
          val branch = baseBranch ?: return@with null
          val headRepoPath = headRepo?.ghRepositoryCoordinates?.repositoryPath
          val baseRepoPath = baseRepo.ghRepositoryCoordinates.repositoryPath
          val showOwner = headRepoPath != null && baseRepoPath != headRepoPath
          baseRepo.ghRepositoryCoordinates.repositoryPath.toString(showOwner) + ":" + branch.name
        }
      },

      { model ->
        with(model) {
          val branch = headBranch ?: return@with null
          val headRepoPath = headRepo?.ghRepositoryCoordinates?.repositoryPath ?: return@with null
          val baseRepoPath = baseRepo.ghRepositoryCoordinates.repositoryPath
          val showOwner = baseRepoPath != headRepoPath
          headRepoPath.toString(showOwner) + ":" + branch.name
        }
      }

    ).create().apply {
      border = BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM),
                                                  JBUI.Borders.empty(7, 8, 8, 8))
    }

    val titleField = JBTextArea(titleDocument).apply {
      background = UIUtil.getListBackground()
      border = BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM),
                                                  JBUI.Borders.empty(8))
      emptyText.text = GithubBundle.message("pull.request.create.title")
      lineWrap = true
    }.also {
      CollaborationToolsUIUtil.overrideUIDependentProperty(it) {
        font = StartupUiUtil.getLabelFont()
      }
      CollaborationToolsUIUtil.registerFocusActions(it)
    }

    val descriptionField = JBTextArea(descriptionDocument).apply {
      background = UIUtil.getListBackground()
      border = JBUI.Borders.empty(8, 8, 0, 8)
      emptyText.text = GithubBundle.message("pull.request.create.description")
      lineWrap = true
    }.also {
      CollaborationToolsUIUtil.overrideUIDependentProperty(it) {
        font = StartupUiUtil.getLabelFont()
      }
      CollaborationToolsUIUtil.registerFocusActions(it)
    }
    descriptionDocument.addAndInvokeEnabledStateListener {
      descriptionField.isEnabled = descriptionDocument.enabled
    }

    val descriptionPane = JBScrollPane(descriptionField,
                                       ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                       ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply {
      isOpaque = false
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
    }

    val metadataPanel = GHPRMetadataPanelFactory(metadataModel, dataContext.avatarIconsProvider).create().apply {
      border = BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM),
                                                  JBUI.Borders.empty(8))
    }
    val createButton = JBOptionButton(createAction, arrayOf(createDraftAction))
    val cancelButton = JButton(cancelAction)
    val actionsPanel = JPanel(HorizontalLayout(JBUIScale.scale(8))).apply {
      add(createButton)
      add(cancelButton)
    }
    val statusPanel = JPanel().apply {
      layout = MigLayout(LC().gridGap("0", "${JBUIScale.scale(8)}").insets("0").fill().flowY().hideMode(3))
      border = JBUI.Borders.empty(8)

      add(createNoChangesWarningLabel(directionModel, commitsCountModel, createLoadingModel), CC().minWidth("0"))
      add(createErrorLabel(createLoadingModel, GithubBundle.message("pull.request.create.error")), CC().minWidth("0"))
      add(createErrorLabel(existenceCheckLoadingModel), CC().minWidth("0"))
      add(createErrorAlreadyExistsLabel(existenceCheckLoadingModel), CC().minWidth("0"))
      add(createLoadingLabel(existenceCheckLoadingModel, existenceCheckProgressIndicator), CC().minWidth("0"))
      add(createLoadingLabel(createLoadingModel, progressIndicator), CC().minWidth("0"))
      add(actionsPanel, CC().minWidth("0"))
    }

    return JPanel(null).apply {
      background = UIUtil.getListBackground()
      layout = MigLayout(LC().gridGap("0", "0").insets("0").fill().flowY())
      isFocusCycleRoot = true
      focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
        override fun getDefaultComponent(aContainer: Container?): Component {
          return if (aContainer == this@apply) titleField
          else super.getDefaultComponent(aContainer)
        }
      }

      add(directionSelector, CC().growX().pushX().minWidth("0"))
      add(titleField, CC().growX().pushX().minWidth("0"))
      add(descriptionPane, CC().grow().push().minWidth("0"))
      add(metadataPanel, CC().growX().pushX())
      add(statusPanel, CC().growX().pushX())
    }
  }

  private inner class InfoController(private val directionModel: MergeDirectionModel<GHGitRepositoryMapping>,
                                     private val existenceCheckLoadingModel: GHIOExecutorLoadingModel<GHPRIdentifier?>,
                                     private val existenceCheckProgressIndicator: ListenableProgressIndicator,
                                     private val createAction: AbstractAction,
                                     private val createDraftAction: AbstractAction) {
    init {
      directionModel.addAndInvokeDirectionChangesListener(::update)
      existenceCheckLoadingModel.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
        override fun onLoadingCompleted() {
          update()
        }
      })
      directionModel.addAndInvokeDirectionChangesListener {
        val baseBranch = directionModel.baseBranch
        val headRepo = directionModel.headRepo
        val headBranch = findCurrentRemoteHead(directionModel)
        if (baseBranch == null || headRepo == null || headBranch == null) existenceCheckLoadingModel.reset()
        else existenceCheckLoadingModel.load(ProgressWrapper.wrap(existenceCheckProgressIndicator)) {
          dataContext.creationService.findPullRequest(it, baseBranch, headRepo, headBranch)
        }
      }
      update()
    }


    private fun update() {
      val enabled = directionModel.let {
        it.baseBranch != null && it.headRepo != null && it.headBranch != null &&
        (findCurrentRemoteHead(it) == null || (existenceCheckLoadingModel.resultAvailable && existenceCheckLoadingModel.result == null))
      }
      createAction.isEnabled = enabled
      createDraftAction.isEnabled = enabled
    }

    private fun findCurrentRemoteHead(directionModel: MergeDirectionModel<GHGitRepositoryMapping>): GitRemoteBranch? {
      val headRepo = directionModel.headRepo ?: return null
      val headBranch = directionModel.headBranch ?: return null
      if (headBranch is GitRemoteBranch) return headBranch
      else headBranch as GitLocalBranch
      val gitRemote = headRepo.gitRemoteUrlCoordinates
      return findPushTarget(gitRemote.repository, gitRemote.remote, headBranch)?.branch
    }
  }

  private inner class CreateAction(private val directionModel: MergeDirectionModel<GHGitRepositoryMapping>,
                                   private val titleDocument: Document, private val descriptionDocument: DisableableDocument,
                                   private val metadataModel: GHPRCreateMetadataModel,
                                   private val draft: Boolean,
                                   private val loadingModel: GHCompletableFutureLoadingModel<GHPullRequestShort>,
                                   private val progressIndicator: ProgressIndicator,
                                   @NlsActions.ActionText name: String) : AbstractAction(name) {

    override fun actionPerformed(e: ActionEvent?) {
      val baseBranch = directionModel.baseBranch ?: return
      val headRepo = directionModel.headRepo ?: return
      val headBranch = directionModel.headBranch ?: return

      val reviewers = metadataModel.reviewers
      val assignees = metadataModel.assignees
      val labels = metadataModel.labels

      loadingModel.future = if (headBranch is GitRemoteBranch) {
        CompletableFuture.completedFuture(headBranch)
      }
      else {
        val dialogMessages = GitPushUtil.BranchNameInputDialogMessages(
          GithubBundle.message("pull.request.create.input.remote.branch.title"),
          GithubBundle.message("pull.request.create.input.remote.branch.name"),
          GithubBundle.message("pull.request.create.input.remote.branch.comment", (headBranch as GitLocalBranch).name,
                               headRepo.gitRemoteUrlCoordinates.remote.name))
        findOrPushRemoteBranch(project,
                               progressIndicator,
                               headRepo.gitRemoteUrlCoordinates.repository,
                               headRepo.gitRemoteUrlCoordinates.remote,
                               headBranch,
                               dialogMessages)
      }.thenCompose { remoteHeadBranch ->
        dataContext.creationService
          .createPullRequest(progressIndicator, baseBranch, headRepo, remoteHeadBranch,
                             titleDocument.text, descriptionDocument.text, draft)
          .thenCompose { adjustReviewers(it, reviewers) }
          .thenCompose { adjustAssignees(it, assignees) }
          .thenCompose { adjustLabels(it, labels) }
          .successOnEdt {
            if (!progressIndicator.isCanceled) {
              viewController.viewPullRequest(it)
              settings.recentNewPullRequestHead = headRepo.ghRepositoryCoordinates
              viewController.resetNewPullRequestView()
            }
            it
          }
      }
    }


    private fun adjustReviewers(pullRequest: GHPullRequestShort, reviewers: List<GHPullRequestRequestedReviewer>)
      : CompletableFuture<GHPullRequestShort> {
      return if (reviewers.isNotEmpty()) {
        dataContext.detailsService.adjustReviewers(ProgressWrapper.wrap(progressIndicator), pullRequest,
                                                   CollectionDelta(emptyList(), reviewers))
          .thenApply { pullRequest }
      }
      else CompletableFuture.completedFuture(pullRequest)
    }

    private fun adjustAssignees(pullRequest: GHPullRequestShort, assignees: List<GHUser>)
      : CompletableFuture<GHPullRequestShort> {
      return if (assignees.isNotEmpty()) {
        dataContext.detailsService.adjustAssignees(ProgressWrapper.wrap(progressIndicator), pullRequest,
                                                   CollectionDelta(emptyList(), assignees))
          .thenApply { pullRequest }
      }
      else CompletableFuture.completedFuture(pullRequest)
    }

    private fun adjustLabels(pullRequest: GHPullRequestShort, labels: List<GHLabel>)
      : CompletableFuture<GHPullRequestShort> {
      return if (labels.isNotEmpty()) {
        dataContext.detailsService.adjustLabels(ProgressWrapper.wrap(progressIndicator), pullRequest,
                                                CollectionDelta(emptyList(), labels))
          .thenApply { pullRequest }
      }
      else CompletableFuture.completedFuture(pullRequest)
    }
  }

  private fun createErrorAlreadyExistsLabel(loadingModel: GHSimpleLoadingModel<GHPRIdentifier?>): JComponent {
    val iconLabel = JLabel(AllIcons.Ide.FatalError)
    val textPane = HtmlEditorPane().apply {
      setBody(HtmlBuilder()
                .append(GithubBundle.message("pull.request.create.already.exists"))
                .appendLink("VIEW", GithubBundle.message("pull.request.create.already.exists.view"))
                .toString())
      removeHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
      addHyperlinkListener(object : HyperlinkAdapter() {
        override fun hyperlinkActivated(e: HyperlinkEvent) {
          if (e.description == "VIEW") {
            loadingModel.result?.let(viewController::viewPullRequest)
          }
          else {
            BrowserUtil.browse(e.description)
          }
        }
      })
    }

    val panel = textPaneWithIcon(iconLabel, textPane)

    fun update() {
      panel.isVisible = loadingModel.resultAvailable && loadingModel.result != null
    }
    loadingModel.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
      override fun onLoadingStarted() = update()
      override fun onLoadingCompleted() = update()
    })
    update()
    return panel
  }

  companion object {
    private val Document.text: String get() = getText(0, length)

    private fun createNoChangesWarningLabel(directionModel: MergeDirectionModel<GHGitRepositoryMapping>,
                                            commitsCountModel: SingleValueModel<Int?>,
                                            loadingModel: GHLoadingModel): JComponent {
      val iconLabel = JLabel(AllIcons.General.Warning)
      val textPane = HtmlEditorPane()

      val panel = textPaneWithIcon(iconLabel, textPane)
      fun update() {
        val commits = commitsCountModel.value
        panel.isVisible = commits == 0 && loadingModel.error == null
        val base = directionModel.baseBranch?.name.orEmpty()
        val head = directionModel.headBranch?.name.orEmpty()
        textPane.setBody(GithubBundle.message("pull.request.create.no.changes", base, head))
      }

      commitsCountModel.addAndInvokeListener { update() }
      loadingModel.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
        override fun onLoadingCompleted() = update()
      })
      update()
      return panel
    }

    private fun createErrorLabel(loadingModel: GHLoadingModel, @Nls prefix: String? = null): JComponent {
      val iconLabel = JLabel(AllIcons.Ide.FatalError)
      val textPane = HtmlEditorPane()

      val panel = textPaneWithIcon(iconLabel, textPane)

      fun update() {
        panel.isVisible = loadingModel.error != null
        textPane.setBody(prefix?.plus(" ").orEmpty() + loadingModel.error?.message.orEmpty())
      }
      loadingModel.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
        override fun onLoadingStarted() = update()
        override fun onLoadingCompleted() = update()
      })
      update()
      return panel
    }

    private fun textPaneWithIcon(iconLabel: JLabel, textPane: HtmlEditorPane) =
      JPanel(MigLayout(LC().insets("0").gridGap("0", "0"))).apply {
        add(iconLabel, CC().alignY("top").gapRight("${iconLabel.iconTextGap}"))
        add(textPane, CC().minWidth("0"))
      }

    private fun createLoadingLabel(loadingModel: GHLoadingModel, progressIndicator: ListenableProgressIndicator): JLabel {
      val label = JLabel(AnimatedIcon.Default())
      fun updateVisible() {
        label.isVisible = loadingModel.loading
      }
      loadingModel.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
        override fun onLoadingStarted() = updateVisible()
        override fun onLoadingCompleted() = updateVisible()
      })
      updateVisible()
      progressIndicator.addAndInvokeListener {
        label.text = progressIndicator.text
      }
      return label
    }
  }
}