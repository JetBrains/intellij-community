// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.collaboration.async.CompletableFutureUtil.completionOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.commits.CommitsBrowserComponentBuilder
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.history.VcsDiffUtil
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.changes.GitChangeUtils
import git4idea.history.GitCommitRequirements
import git4idea.history.GitLogUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.*
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesTreeFactory
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRDiffController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabComponentController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRViewTabsFactory
import org.jetbrains.plugins.github.ui.util.DisableableDocument
import org.jetbrains.plugins.github.util.DiffRequestChainProducer
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.text.Document
import javax.swing.text.PlainDocument


internal class GHPRCreateComponentHolder(private val actionManager: ActionManager,
                                         private val project: Project,
                                         private val settings: GithubPullRequestsProjectUISettings,
                                         private val repositoriesManager: GHProjectRepositoriesManager,
                                         private val dataContext: GHPRDataContext,
                                         private val viewController: GHPRToolWindowTabComponentController,
                                         disposable: Disposable) {

  private val repositoryDataService = dataContext.repositoryDataService

  private val directionModel = GHPRMergeDirectionModelImpl(repositoryDataService.repositoryMapping, repositoriesManager)
  private val titleDocument = PlainDocument()
  private val descriptionDocument = DisableableDocument()
  private val metadataModel = GHPRCreateMetadataModel(repositoryDataService, dataContext.securityService.currentUser)

  private val commitSelectionModel = SingleValueModel<VcsCommitMetadata?>(null)

  private val changesLoadingModel = GHIOExecutorLoadingModel<Collection<Change>>(disposable)
  private val commitsLoadingModel = GHIOExecutorLoadingModel<List<VcsCommitMetadata>>(disposable)
  private val commitChangesLoadingModel = GHIOExecutorLoadingModel<Collection<Change>>(disposable)
  private val filesCountFlow = changesLoadingModel.getResultFlow().map { it?.size }
  private val commitsCountFlow = commitsLoadingModel.getResultFlow().map { it?.size }
  private val commitsCountModel = createCountModel(commitsLoadingModel)

  private val existenceCheckLoadingModel = GHIOExecutorLoadingModel<GHPRIdentifier?>(disposable)
  private val createLoadingModel = GHCompletableFutureLoadingModel<GHPullRequestShort>(disposable)

  init {
    directionModel.addAndInvokeDirectionChangesListener {
      checkLoadChanges(true)
      commitSelectionModel.value = null
    }
    commitSelectionModel.addAndInvokeListener {
      checkLoadSelectedCommitChanges(true)
    }
    project.messageBus.connect(disposable).subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { repository ->
      if (repository == repositoryDataService.remoteCoordinates.repository) {
        runInEdt {
          checkLoadChanges(false)
          checkLoadSelectedCommitChanges(false)
          checkUpdateHead()
        }
      }
    })
    resetModel()
  }

  private fun checkLoadChanges(clear: Boolean) {
    if (clear) {
      changesLoadingModel.reset()
      commitsLoadingModel.reset()
    }

    val baseBranch = directionModel.baseBranch
    val headRepo = directionModel.headRepo
    val headBranch = directionModel.headBranch
    if (baseBranch == null || headRepo == null || headBranch == null) {
      changesLoadingModel.reset()
      commitsLoadingModel.reset()
      return
    }

    val repository = headRepo.gitRemoteUrlCoordinates.repository
    changesLoadingModel.load(EmptyProgressIndicator()) {
      GitChangeUtils.getThreeDotDiff(repository, baseBranch.name, headBranch.name)
    }
    commitsLoadingModel.load(EmptyProgressIndicator()) {
      GitLogUtil.collectMetadata(project, repository.root, "${baseBranch.name}..${headBranch.name}").commits
    }
  }

  private fun checkLoadSelectedCommitChanges(clear: Boolean) {
    val commit = commitSelectionModel.value
    if (clear) commitChangesLoadingModel.reset()
    if (commit != null)
      commitChangesLoadingModel.load(EmptyProgressIndicator()) {
        val changes = mutableListOf<Change>()
        GitLogUtil.readFullDetailsForHashes(project, dataContext.repositoryDataService.remoteCoordinates.repository.root,
                                            listOf(commit.id.asString()),
                                            GitCommitRequirements.DEFAULT) { gitCommit -> changes.addAll(gitCommit.changes) }
        changes
      }
  }

  private fun checkUpdateHead() {
    val headRepo = directionModel.headRepo
    if (headRepo != null && !directionModel.headSetByUser) directionModel.setHead(headRepo,
                                                                                  headRepo.gitRemoteUrlCoordinates.repository.currentBranch)
  }

  private val changesLoadingErrorHandler = GHRetryLoadingErrorHandler {
    checkLoadChanges(false)
  }
  private val commitsLoadingErrorHandler = GHRetryLoadingErrorHandler {
    checkLoadChanges(false)
  }

  private val diffRequestProducer = NewPRDiffRequestChainProducer()
  private val diffController = GHPRDiffController(dataContext.newPRDiffModel, diffRequestProducer)

  private val uiDisposable = Disposer.newDisposable().also {
    Disposer.register(disposable, it)
  }

  val component by lazy {
    val infoComponent = GHPRCreateInfoComponentFactory(project, settings, dataContext, viewController)
      .create(directionModel, titleDocument, descriptionDocument, metadataModel, commitsCountModel, existenceCheckLoadingModel,
              createLoadingModel)

    GHPRViewTabsFactory(project, viewController::viewList, uiDisposable)
      .create(infoComponent, diffController,
              createFilesComponent(), filesCountFlow, null,
              createCommitsComponent(), commitsCountFlow).apply {
        setDataProvider { dataId ->
          if (DiffRequestChainProducer.DATA_KEY.`is`(dataId)) diffRequestProducer
          else null
        }
      }.component
  }

  private fun createFilesComponent(): JComponent {
    val panel = BorderLayoutPanel().withBackground(UIUtil.getListBackground())
    val changesLoadingPanel = GHLoadingPanelFactory(changesLoadingModel,
                                                    GithubBundle.message("pull.request.create.select.branches"),
                                                    GithubBundle.message("cannot.load.changes"),
                                                    changesLoadingErrorHandler)
      .withContentListener {
        diffController.filesTree = UIUtil.findComponentOfType(it, ChangesTree::class.java)
      }
      .createWithUpdatesStripe(uiDisposable) { parent, model ->
        createChangesTree(parent, model, GithubBundle.message("pull.request.does.not.contain.changes"))
      }.apply {
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
      }
    val toolbar = GHPRChangesTreeFactory.createTreeToolbar(actionManager, changesLoadingPanel)
    return panel.addToTop(toolbar).addToCenter(changesLoadingPanel)
  }

  private fun createCommitsComponent(): JComponent {
    val splitter = OnePixelSplitter(true, "Github.PullRequest.Commits.Component", 0.4f).apply {
      isOpaque = true
      background = UIUtil.getListBackground()
    }

    val commitsLoadingPanel = GHLoadingPanelFactory(commitsLoadingModel,
                                                    GithubBundle.message("pull.request.create.select.branches"),
                                                    GithubBundle.message("cannot.load.commits"),
                                                    commitsLoadingErrorHandler)
      .createWithUpdatesStripe(uiDisposable) { _, model ->
        CommitsBrowserComponentBuilder(project, model)
          .installPopupActions(DefaultActionGroup(actionManager.getAction("Github.PullRequest.Changes.Reload")), "GHPRCommitsPopup")
          .setEmptyCommitListText(GithubBundle.message("pull.request.does.not.contain.commits"))
          .onCommitSelected { commitSelectionModel.value = it }
          .create()
      }

    val changesLoadingPanel = GHLoadingPanelFactory(commitChangesLoadingModel,
                                                    GithubBundle.message("pull.request.select.commit.to.view.changes"),
                                                    GithubBundle.message("cannot.load.changes"))
      .withContentListener {
        diffController.commitsTree = UIUtil.findComponentOfType(it, ChangesTree::class.java)
      }
      .createWithModel { parent, model ->
        createChangesTree(parent, model, GithubBundle.message("pull.request.commit.does.not.contain.changes"))
      }.apply {
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
      }
    val toolbar = GHPRChangesTreeFactory.createTreeToolbar(actionManager, changesLoadingPanel)
    val changesBrowser = BorderLayoutPanel().andTransparent()
      .addToTop(toolbar)
      .addToCenter(changesLoadingPanel)

    return splitter.apply {
      firstComponent = commitsLoadingPanel
      secondComponent = changesBrowser
    }
  }

  private fun createChangesTree(parentPanel: JPanel,
                                model: SingleValueModel<Collection<Change>>,
                                emptyTextText: String): JComponent {
    val tree = GHPRChangesTreeFactory(project, model).create(emptyTextText).also {
      it.doubleClickHandler = Processor { e ->
        if (EditSourceOnDoubleClickHandler.isToggleEvent(it, e)) return@Processor false
        viewController.openNewPullRequestDiff(true)
        true
      }
      it.enterKeyHandler = Processor {
        viewController.openNewPullRequestDiff(true)
        true
      }
    }

    DataManager.registerDataProvider(parentPanel) {
      if (tree.isShowing) tree.getData(it) else null
    }
    return ScrollPaneFactory.createScrollPane(tree, true)
  }

  @Suppress("DuplicatedCode")
  private fun createCountModel(loadingModel: GHSimpleLoadingModel<out Collection<*>>): SingleValueModel<Int?> {
    val model = SingleValueModel<Int?>(null)
    val loadingListener = object : GHLoadingModel.StateChangeListener {
      override fun onLoadingCompleted() {
        val items = if (loadingModel.resultAvailable) loadingModel.result!! else null
        model.value = items?.size
      }
    }
    loadingModel.addStateChangeListener(loadingListener)
    loadingListener.onLoadingCompleted()
    return model
  }

  fun resetModel() {
    existenceCheckLoadingModel.reset()
    createLoadingModel.future = null

    commitSelectionModel.value = null

    changesLoadingModel.reset()
    commitsLoadingModel.reset()
    commitChangesLoadingModel.reset()

    metadataModel.assignees = emptyList()
    metadataModel.reviewers = emptyList()
    metadataModel.labels = emptyList()

    descriptionDocument.clear()
    descriptionDocument.loadContent(GithubBundle.message("pull.request.create.loading.template")) {
      GHPRTemplateLoader.readTemplate(project)
    }
    titleDocument.clear()

    directionModel.preFill()
  }

  private fun GHPRMergeDirectionModelImpl.preFill() {
    val repositoryDataService = dataContext.repositoryDataService
    val currentRemote = repositoryDataService.repositoryMapping.gitRemoteUrlCoordinates
    val currentRepo = currentRemote.repository

    val baseRepo = GHGitRepositoryMapping(repositoryDataService.repositoryCoordinates, currentRemote)

    val branches = currentRepo.branches
    val defaultBranchName = repositoryDataService.defaultBranchName
    if (defaultBranchName != null) {
      baseBranch = branches.findRemoteBranch("${currentRemote.remote.name}/$defaultBranchName")
    }
    else {
      baseBranch = branches.findRemoteBranch("${currentRemote.remote.name}/master")
      if (baseBranch == null) {
        baseBranch = branches.findRemoteBranch("${currentRemote.remote.name}/main")
      }
    }

    val repos = repositoriesManager.knownRepositories
    val baseIsFork = repositoryDataService.isFork
    val recentHead = settings.recentNewPullRequestHead
    val headRepo = repos.find { it.ghRepositoryCoordinates == recentHead } ?: when {
      repos.size == 1 -> repos.single()
      baseIsFork -> baseRepo
      else -> repos.find { it.gitRemoteUrlCoordinates.remote.name == "origin" }
    }

    val headBranch = headRepo?.gitRemoteUrlCoordinates?.repository?.currentBranch
    setHead(headRepo, headBranch)
    headSetByUser = false
  }

  private fun Document.clear() {
    if (length > 0) {
      remove(0, length)
    }
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

  private inner class NewPRDiffRequestChainProducer : DiffRequestChainProducer {
    override fun getRequestChain(changes: ListSelection<Change>): DiffRequestChain {
      val producers = changes.map {
        val requestDataKeys = mutableMapOf<Key<out Any>, Any?>()

        if (diffController.activeTree == GHPRDiffController.ActiveTree.FILES) {
          val baseBranchName = directionModel.baseBranch?.name ?: "Base"
          requestDataKeys[DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE] =
            VcsDiffUtil.getRevisionTitle(baseBranchName, it.beforeRevision?.file, it.afterRevision?.file)

          val headBranchName = directionModel.headBranch?.name ?: "Head"
          requestDataKeys[DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE] =
            VcsDiffUtil.getRevisionTitle(headBranchName, it.afterRevision?.file, null)
        }
        else {
          VcsDiffUtil.putFilePathsIntoChangeContext(it, requestDataKeys)
        }

        ChangeDiffRequestProducer.create(project, it, requestDataKeys)
      }
      return ChangeDiffRequestChain(producers.list, producers.selectedIndex)
    }
  }
}