// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.CommonBundle
import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.push.PushSpec
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.messages.MessagesService
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitVcs
import git4idea.push.GitPushOperation
import git4idea.push.GitPushSource
import git4idea.push.GitPushSupport
import git4idea.push.GitPushTarget
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.validators.GitRefNameValidator
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
import org.jetbrains.plugins.github.pullrequest.ui.*
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRMetadataPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabComponentController
import org.jetbrains.plugins.github.ui.util.DisableableDocument
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.*
import java.awt.Component
import java.awt.Container
import java.awt.event.ActionEvent
import java.util.concurrent.CompletableFuture
import javax.swing.*
import javax.swing.text.Document

internal class GHPRCreateInfoComponentFactory(private val project: Project,
                                              private val settings: GithubPullRequestsProjectUISettings,
                                              private val repositoriesManager: GHProjectRepositoriesManager,
                                              private val dataContext: GHPRDataContext,
                                              private val viewController: GHPRToolWindowTabComponentController) {

  fun create(directionModel: GHPRCreateDirectionModel,
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
    directionModel.preFill()
    descriptionDocument.loadContent(GithubBundle.message("pull.request.create.loading.template")) {
      GHPRTemplateLoader.readTemplate(project)
    }

    val directionSelector = GHPRCreateDirectionComponentFactory(repositoriesManager, directionModel).create().apply {
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
      GHUIUtil.overrideUIDependentProperty(it) {
        font = UIUtil.getLabelFont()
      }
      GHUIUtil.registerFocusActions(it)
    }

    val descriptionField = JBTextArea(descriptionDocument).apply {
      background = UIUtil.getListBackground()
      border = JBUI.Borders.empty(8, 8, 0, 8)
      emptyText.text = GithubBundle.message("pull.request.create.description")
      lineWrap = true
    }.also {
      GHUIUtil.overrideUIDependentProperty(it) {
        font = UIUtil.getLabelFont()
      }
      GHUIUtil.registerFocusActions(it)
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
    val statusPanel = JPanel(VerticalLayout(8, SwingConstants.LEFT)).apply {
      border = JBUI.Borders.empty(8)

      add(createNoChangesWarningLabel(directionModel, commitsCountModel))
      add(createErrorLabel(createLoadingModel))
      add(createErrorLabel(existenceCheckLoadingModel))
      add(createErrorAlreadyExistsLabel(existenceCheckLoadingModel))
      add(createLoadingLabel(existenceCheckLoadingModel, existenceCheckProgressIndicator))
      add(createLoadingLabel(createLoadingModel, progressIndicator))
      add(actionsPanel)
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

  private inner class InfoController(private val directionModel: GHPRCreateDirectionModel,
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
    }


    private fun update() {
      val enabled = directionModel.let {
        it.baseBranch != null && it.headRepo != null && it.headBranch != null &&
        (findCurrentRemoteHead(it) == null || (existenceCheckLoadingModel.resultAvailable && existenceCheckLoadingModel.result == null))
      }
      createAction.isEnabled = enabled
      createDraftAction.isEnabled = enabled
    }

    private fun findCurrentRemoteHead(directionModel: GHPRCreateDirectionModel): GitRemoteBranch? {
      val headRepo = directionModel.headRepo ?: return null
      val headBranch = directionModel.headBranch ?: return null
      if (headBranch is GitRemoteBranch) return headBranch
      else headBranch as GitLocalBranch
      val gitRemote = headRepo.gitRemote
      return GithubGitHelper.findPushTarget(gitRemote.repository, gitRemote.remote, headBranch)?.branch
    }
  }

  private fun GHPRCreateDirectionModel.preFill() {
    val repositoryDataService = dataContext.repositoryDataService
    val currentRemote = repositoryDataService.repositoryMapping.gitRemote
    val currentRepo = currentRemote.repository

    val baseRepo = GHGitRepositoryMapping(repositoryDataService.repositoryCoordinates, currentRemote)

    baseBranch = currentRepo.branches.findRemoteBranch("${currentRemote.remote.name}/${repositoryDataService.defaultBranchName}")

    val repos = repositoriesManager.knownRepositories
    val baseIsFork = repositoryDataService.isFork
    val recentHead = settings.recentNewPullRequestHead
    val headRepo = repos.find { it.repository == recentHead } ?: when {
      repos.size == 1 -> repos.single()
      baseIsFork -> baseRepo
      else -> repos.find { it.gitRemote.remote.name == "origin" }
    }

    val headBranch = headRepo?.gitRemote?.repository?.currentBranch
    setHead(headRepo, headBranch)
    headSetByUser = false
  }

  private fun DisableableDocument.loadContent(@Nls loadingText: String, loader: () -> CompletableFuture<String?>) {
    enabled = false
    insertString(0, loadingText, null)
    loader().successOnEdt {
      if (!enabled) {
        remove(0, length)
        insertString(0, it, null)
      }
    }.completionOnEdt {
      if (!enabled) enabled = true
    }
  }

  private inner class CreateAction(private val directionModel: GHPRCreateDirectionModel,
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
        findOrPushRemoteBranch(progressIndicator,
                               headRepo.gitRemote.repository,
                               headRepo.gitRemote.remote,
                               headBranch as GitLocalBranch)
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
              settings.recentNewPullRequestHead = headRepo.repository
              viewController.resetNewPullRequestView()
            }
            it
          }
      }
    }

    private fun findOrPushRemoteBranch(progressIndicator: ProgressIndicator,
                                       repository: GitRepository,
                                       remote: GitRemote,
                                       localBranch: GitLocalBranch): CompletableFuture<GitRemoteBranch> {

      val gitPushSupport = DvcsUtil.getPushSupport(GitVcs.getInstance(project)) as? GitPushSupport
                           ?: return CompletableFuture.failedFuture(ProcessCanceledException())

      val existingPushTarget = GithubGitHelper.findPushTarget(repository, remote, localBranch)
      if (existingPushTarget != null) {
        val localHash = repository.branches.getHash(localBranch)
        val remoteHash = repository.branches.getHash(existingPushTarget.branch)
        if (localHash == remoteHash) return CompletableFuture.completedFuture(existingPushTarget.branch)
      }

      val pushTarget = existingPushTarget
                       ?: inputPushTarget(repository, remote, localBranch)
                       ?: return CompletableFuture.failedFuture(ProcessCanceledException())

      val future = CompletableFuture<GitRemoteBranch>()
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(
        object : Task.Backgroundable(repository.project, DvcsBundle.message("push.process.pushing"), true) {

          override fun run(indicator: ProgressIndicator) {
            indicator.text = DvcsBundle.message("push.process.pushing")
            val pushSpec = PushSpec(GitPushSource.create(localBranch), pushTarget)
            val pushResult = GitPushOperation(repository.project, gitPushSupport, mapOf(repository to pushSpec), null, false, false)
                               .execute().results[repository] ?: error("Missing push result")
            check(pushResult.error == null) {
              GithubBundle.message("pull.request.create.push.failed", pushResult.error.orEmpty())
            }
          }

          override fun onSuccess() {
            future.complete(pushTarget.branch)
          }

          override fun onThrowable(error: Throwable) {
            future.completeExceptionally(error)
          }

          override fun onCancel() {
            future.completeExceptionally(ProcessCanceledException())
          }
        }, progressIndicator)
      return future
    }

    private fun inputPushTarget(repository: GitRepository, remote: GitRemote, localBranch: GitLocalBranch): GitPushTarget? {
      val branchName = MessagesService.getInstance().showInputDialog(repository.project, null,
                                                                     GithubBundle.message("pull.request.create.input.remote.branch.name"),
                                                                     GithubBundle.message("pull.request.create.input.remote.branch.title"),
                                                                     null, localBranch.name, null, null,
                                                                     GithubBundle.message("pull.request.create.input.remote.branch.comment",
                                                                                          localBranch.name, remote.name))
                       ?: return null
      //always set tracking
      return GitPushTarget(GitStandardRemoteBranch(remote, GitRefNameValidator.getInstance().cleanUpBranchName(branchName)), true)
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
    val label = JLabel(AllIcons.Ide.FatalError).apply {
      foreground = UIUtil.getErrorForeground()
      text = GithubBundle.message("pull.request.create.already.exists")
    }
    val link = ActionLink(GithubBundle.message("pull.request.create.already.exists.view")) {
      loadingModel.result?.let(viewController::viewPullRequest)
    }
    val panel = JPanel(HorizontalLayout(10)).apply {
      add(label)
      add(link)
    }

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

    private fun createNoChangesWarningLabel(directionModel: GHPRCreateDirectionModel,
                                            commitsCountModel: SingleValueModel<Int?>): JComponent {
      val label = JLabel(AllIcons.General.Warning)
      fun update() {
        val commits = commitsCountModel.value
        label.isVisible = commits == 0
        val base = directionModel.baseBranch?.name.orEmpty()
        val head = directionModel.headBranch?.name.orEmpty()
        label.text = GithubBundle.message("pull.request.create.no.changes", base, head)
      }

      commitsCountModel.addValueChangedListener(::update)
      commitsCountModel.addAndInvokeValueChangedListener(::update)
      return label
    }

    private fun createErrorLabel(loadingModel: GHLoadingModel): JLabel {
      val label = JLabel(AllIcons.Ide.FatalError).apply {
        foreground = UIUtil.getErrorForeground()
      }

      fun update() {
        label.isVisible = loadingModel.error != null
        label.text = loadingModel.error?.message
      }
      loadingModel.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
        override fun onLoadingStarted() = update()
        override fun onLoadingCompleted() = update()
      })
      update()
      return label
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

    private class ListenableProgressIndicator : AbstractProgressIndicatorExBase(), StandardProgressIndicator {
      private val eventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

      override fun isReuseable() = true
      override fun onProgressChange() = invokeAndWaitIfNeeded { eventDispatcher.multicaster.eventOccurred() }
      fun addAndInvokeListener(listener: () -> Unit) = SimpleEventListener.addAndInvokeListener(eventDispatcher, listener)
      override fun cancel() = super.cancel()
      override fun isCanceled() = super.isCanceled()
    }
  }
}