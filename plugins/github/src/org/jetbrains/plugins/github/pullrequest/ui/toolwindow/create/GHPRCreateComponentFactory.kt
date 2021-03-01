// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.ActionManager
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
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.*
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesTreeFactory
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRCommitsBrowserComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRDiffController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabComponentController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRViewTabsFactory
import org.jetbrains.plugins.github.ui.util.DisableableDocument
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.DiffRequestChainProducer
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.text.PlainDocument


internal class GHPRCreateComponentFactory(private val actionManager: ActionManager,
                                          private val project: Project,
                                          private val settings: GithubPullRequestsProjectUISettings,
                                          private val repositoriesManager: GHProjectRepositoriesManager,
                                          private val dataContext: GHPRDataContext,
                                          private val viewController: GHPRToolWindowTabComponentController,
                                          disposable: Disposable) {

  private val repositoryDataService = dataContext.repositoryDataService

  private val directionModel = GHPRCreateDirectionModelImpl(repositoryDataService.repositoryMapping)

  private val commitSelectionModel = SingleValueModel<VcsCommitMetadata?>(null)

  private val changesLoadingModel = GHIOExecutorLoadingModel<Collection<Change>>(disposable)
  private val commitsLoadingModel = GHIOExecutorLoadingModel<List<VcsCommitMetadata>>(disposable)
  private val commitChangesLoadingModel = GHIOExecutorLoadingModel<Collection<Change>>(disposable)

  init {
    directionModel.addAndInvokeDirectionChangesListener {
      checkLoadChanges(true)
      commitSelectionModel.value = null
    }
    commitSelectionModel.addAndInvokeValueChangedListener {
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

    val repository = headRepo.gitRemote.repository
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
    if (headRepo != null && !directionModel.headSetByUser) directionModel.setHead(headRepo, headRepo.gitRemote.repository.currentBranch)
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

  fun create(): JComponent {
    val filesCountModel = createCountModel(changesLoadingModel)
    val commitsCountModel = createCountModel(commitsLoadingModel)

    val titleDocument = PlainDocument()
    val descriptionDocument = DisableableDocument()
    val metadataModel = GHPRCreateMetadataModel(repositoryDataService, dataContext.securityService.currentUser)
    val existenceCheckLoadingModel = GHIOExecutorLoadingModel<GHPRIdentifier?>(uiDisposable)
    val createLoadingModel = GHCompletableFutureLoadingModel<GHPullRequestShort>(uiDisposable)

    val infoComponent = GHPRCreateInfoComponentFactory(project, settings, repositoriesManager, dataContext, viewController)
      .create(directionModel, titleDocument, descriptionDocument, metadataModel, commitsCountModel, existenceCheckLoadingModel,
              createLoadingModel)

    return GHPRViewTabsFactory(project, viewController::viewList, uiDisposable)
      .create(infoComponent, diffController,
              createFilesComponent(), filesCountModel,
              createCommitsComponent(), commitsCountModel).apply {
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
        GHPRCommitsBrowserComponentFactory(project).create(model) { commit ->
          commitSelectionModel.value = commit
        }
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