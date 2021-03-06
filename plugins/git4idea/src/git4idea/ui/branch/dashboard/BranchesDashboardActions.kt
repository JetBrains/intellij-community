// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.DvcsUtil.disableActionIfAnyRepositoryIsFresh
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.dvcs.diverged
import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.VcsLogProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.actions.BooleanPropertyToggleAction
import com.intellij.vcs.log.util.VcsLogUtil.HEAD
import git4idea.GitUtil
import git4idea.actions.GitFetch
import git4idea.branch.GitBranchType
import git4idea.branch.GitBrancher
import git4idea.config.GitVcsSettings
import git4idea.fetch.GitFetchResult
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle.message
import git4idea.i18n.GitBundleExtensions.messagePointer
import git4idea.isRemoteBranchProtected
import git4idea.remote.editRemote
import git4idea.remote.removeRemotes
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.*
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

internal object BranchesDashboardActions {

  class BranchesTreeActionGroup(private val project: Project, private val tree: FilteringBranchesTree) : ActionGroup(), DumbAware {

    init {
      isPopup = true
    }

    override fun hideIfNoVisibleChildren() = true

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      BranchActionsBuilder(project, tree).build()?.getChildren(e) ?: AnAction.EMPTY_ARRAY
  }

  class MultipleLocalBranchActions(private val containsRemoteBranches: Boolean, private val repository: GitRepository) : ActionGroup(), DumbAware {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      val commonActions: Array<AnAction> = arrayOf(ShowArbitraryBranchesDiffAction(), UpdateSelectedBranchAction(), DeleteBranchAction())
      return if (containsRemoteBranches) commonActions + arrayOf<AnAction>(Separator(), EditRemoteAction(repository), RemoveRemoteAction(repository)) else commonActions
    }
  }

  class CurrentBranchActions(project: Project,
                             repositories: List<GitRepository>,
                             branchName: String,
                             currentRepository: GitRepository)
    : GitBranchPopupActions.CurrentBranchActions(project, repositories, branchName, currentRepository) {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      val children = arrayListOf<AnAction>(*super.getChildren(e))
      if (myRepositories.diverged()) {
        children.add(1, CheckoutAction(myProject, myRepositories, myBranchName))
      }
      return children.toTypedArray()
    }
  }

  class LocalBranchActions(project: Project,
                           repositories: List<GitRepository>,
                           branchName: String,
                           currentRepository: GitRepository)
    : GitBranchPopupActions.LocalBranchActions(project, repositories, branchName, currentRepository) {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      arrayListOf<AnAction>(*super.getChildren(e)).toTypedArray()
  }

  class RemoteBranchActions(project: Project,
                            repositories: List<GitRepository>,
                            @NonNls branchName: String,
                            private val currentRepository: GitRepository)
    : GitBranchPopupActions.RemoteBranchActions(project, repositories, branchName, currentRepository) {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      arrayListOf<AnAction>(*super.getChildren(e), Separator(), EditRemoteAction(currentRepository), RemoveRemoteAction(currentRepository))
        .toTypedArray()
  }

  class GroupActions(private val currentRepository: GitRepository) : ActionGroup(), DumbAware {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      arrayListOf<AnAction>(EditRemoteAction(currentRepository), RemoveRemoteAction(currentRepository)).toTypedArray()
  }

  class MultipleGroupActions(private val currentRepository: GitRepository) : ActionGroup(), DumbAware {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      arrayListOf<AnAction>(RemoveRemoteAction(currentRepository)).toTypedArray()
  }

  class RemoteGlobalActions : ActionGroup(), DumbAware {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      arrayListOf<AnAction>(ActionManager.getInstance().getAction("Git.Configure.Remotes")).toTypedArray()
  }

  class BranchActionsBuilder(private val project: Project, private val tree: FilteringBranchesTree) {
    fun build(): ActionGroup? {
      val selectedBranches = tree.getSelectedBranches()
      val multipleBranchSelection = selectedBranches.size > 1
      val guessRepo = DvcsUtil.guessCurrentRepositoryQuick(project, GitUtil.getRepositoryManager(project),
                                                           GitVcsSettings.getInstance(project).recentRootPath) ?: return null
      if (multipleBranchSelection) {
        return MultipleLocalBranchActions(selectedBranches.any { !it.isLocal }, guessRepo)
      }

      val branchInfo = selectedBranches.singleOrNull()
      val headSelected = tree.getSelectedBranchFilters().contains(HEAD)
      if (branchInfo != null && !headSelected) {
        return when {
          branchInfo.isCurrent -> CurrentBranchActions(project, branchInfo.repositories, branchInfo.branchName, guessRepo)
          branchInfo.isLocal -> LocalBranchActions(project, branchInfo.repositories, branchInfo.branchName, guessRepo)
          else -> RemoteBranchActions(project, branchInfo.repositories, branchInfo.branchName, guessRepo)
        }
      }

      val selectedRemotes = tree.getSelectedRemotes()
      if (selectedRemotes.size == 1) {
        return GroupActions(guessRepo)
      }
      else if (selectedRemotes.isNotEmpty()) {
        return MultipleGroupActions(guessRepo)
      }

      val selectedBranchNodes = tree.getSelectedBranchNodes()
      if (selectedBranchNodes.size == 1 && selectedBranchNodes.first().type == NodeType.REMOTE_ROOT) {
        return RemoteGlobalActions()
      }

      val currentBranchName = guessRepo.currentBranchName
      if (currentBranchName != null && headSelected) {
        return CurrentBranchActions(project, listOf(guessRepo), currentBranchName, guessRepo)
      }

      return null
    }
  }

  class NewBranchAction : BranchesActionBase({ DvcsBundle.message("new.branch.action.text") },
                                             { DvcsBundle.message("new.branch.action.text") },
                                             com.intellij.dvcs.ui.NewBranchAction.icon) {

    override fun update(e: AnActionEvent) {
      val branchFilters = e.getData(GIT_BRANCH_FILTERS)
      if (branchFilters != null && branchFilters.contains(HEAD)) {
        e.presentation.isEnabled = true
      }
      else {
        super.update(e)
      }
    }

    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {
      if (branches.size > 1) {
        e.presentation.isEnabled = false
        e.presentation.description = message("action.Git.New.Branch.description")
        return
      }

      val repositories = branches.flatMap(BranchInfo::repositories).distinct()
      disableActionIfAnyRepositoryIsFresh(e, repositories, DvcsBundle.message("action.not.possible.in.fresh.repo.new.branch"))
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project!!
      val branchFilters = e.getData(GIT_BRANCH_FILTERS)
      if (branchFilters != null && branchFilters.contains(HEAD)) {
        createOrCheckoutNewBranch(project, GitRepositoryManager.getInstance(project).repositories, HEAD)
      }
      else {
        val branches = e.getData(GIT_BRANCHES)!!
        val repositories = branches.flatMap(BranchInfo::repositories).distinct()
        val branchName = branches.first().branchName
        createOrCheckoutNewBranch(project, repositories, "$branchName^0", message("action.Git.New.Branch.dialog.title", branchName))
      }
    }
  }

  class UpdateSelectedBranchAction : BranchesActionBase(text = messagePointer("action.Git.Update.Selected.text"),
                                                        icon = AllIcons.Actions.CheckOut) {
    override fun update(e: AnActionEvent) {
      val enabledAndVisible = e.project?.let(::hasRemotes) ?: false
      e.presentation.isEnabledAndVisible = enabledAndVisible

      if (enabledAndVisible) {
        super.update(e)
      }
    }

    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {
      val presentation = e.presentation
      if (GitFetchSupport.fetchSupport(project).isFetchRunning) {
        presentation.isEnabled = false
        presentation.description = message("action.Git.Update.Selected.description.already.running")
        return
      }
      val repositories = branches.flatMap(BranchInfo::repositories).distinct()
      val branchNames = branches.map(BranchInfo::branchName)
      val updateMethodName = GitVcsSettings.getInstance(project).updateMethod.name.toLowerCase()
      presentation.description = message("action.Git.Update.Selected.description", branches.size, updateMethodName)
      val trackingInfosExist = isTrackingInfosExist(branchNames, repositories)
      presentation.isEnabled = trackingInfosExist
      if (!trackingInfosExist) {
        presentation.description = message("action.Git.Update.Selected.description.tracking.not.configured", branches.size)
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val branches = e.getData(GIT_BRANCHES)!!
      val project = e.project!!
      val repositories = branches.flatMap(BranchInfo::repositories).distinct()
      val branchNames = branches.map(BranchInfo::branchName)
      updateBranches(project, repositories, branchNames)
    }
  }

  class DeleteBranchAction : BranchesActionBase(icon = AllIcons.Actions.GC) {
    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {
      e.presentation.text = message("action.Git.Delete.Branch.title", branches.size)
      val disabled = branches.any { it.isCurrent || (!it.isLocal && isRemoteBranchProtected(it.repositories, it.branchName)) }
      e.presentation.isEnabled = !disabled
    }

    override fun actionPerformed(e: AnActionEvent) {
      val branches = e.getData(GIT_BRANCHES)!!
      val project = e.project!!
      delete(project, branches)
    }

    private fun delete(project: Project, branches: Collection<BranchInfo>) {
      val gitBrancher = GitBrancher.getInstance(project)
      val (localBranches, remoteBranches) = branches.partition { it.isLocal && !it.isCurrent }
      with(gitBrancher) {
        val branchesToContainingRepositories: Map<String, List<GitRepository>> = localBranches.associate { it.branchName to it.repositories }
        val localBranchNames = branchesToContainingRepositories.keys
        val deleteRemoteBranches = {
          deleteRemoteBranches(remoteBranches.map(BranchInfo::branchName), remoteBranches.flatMap(BranchInfo::repositories).distinct())
        }
        if (localBranchNames.isNotEmpty()) { //delete local (possible tracked) branches first if any
          deleteBranches(branchesToContainingRepositories, deleteRemoteBranches)
        }
        else {
          deleteRemoteBranches()
        }
      }
    }
  }

  class ShowBranchDiffAction : BranchesActionBase(text = messagePointer("action.Git.Compare.With.Current.title"),
                                                  icon = AllIcons.Actions.Diff) {
    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {
      if (branches.none { !it.isCurrent }) {
        e.presentation.isEnabled = false
        e.presentation.description = message("action.Git.Update.Selected.description.select.non.current")
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val branches = e.getData(GIT_BRANCHES)!!
      val project = e.project!!
      val gitBrancher = GitBrancher.getInstance(project)

      for (branch in branches.filterNot(BranchInfo::isCurrent)) {
        gitBrancher.compare(branch.branchName, branch.repositories)
      }
    }
  }

  class ShowArbitraryBranchesDiffAction : BranchesActionBase(text = messagePointer("action.Git.Compare.Selected.title"),
                                                             icon = AllIcons.Actions.Diff) {
    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {
      if (branches.size != 2) {
        e.presentation.isEnabledAndVisible = false
        e.presentation.description = ""
      }
      else {
        e.presentation.description=message("action.Git.Compare.Selected.description")
        val commonRepositories = branches.elementAt(0).repositories intersect branches.elementAt(1).repositories
        if (commonRepositories.isEmpty()) {
          e.presentation.isEnabled = false
          e.presentation.description = message("action.Git.Compare.Selected.description.disabled")
        }
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val branches = e.getData(GIT_BRANCHES)!!
      val branchOne = branches.elementAt(0)
      val branchTwo = branches.elementAt(1)
      val commonRepositories = branchOne.repositories intersect branchTwo.repositories
      val gitBrancher = GitBrancher.getInstance(e.project!!)

      gitBrancher.compareAny(branchOne.branchName, branchTwo.branchName, commonRepositories.toList())
    }
  }

  class ShowMyBranchesAction(private val uiController: BranchesDashboardController)
    : ToggleAction(messagePointer("action.Git.Show.My.Branches.title"), AllIcons.Actions.Find), DumbAware {

    override fun isSelected(e: AnActionEvent) = uiController.showOnlyMy

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      uiController.showOnlyMy = state
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      val project = e.getData(CommonDataKeys.PROJECT)
      if (project == null) {
        e.presentation.isEnabled = false
        return
      }
      val log = VcsProjectLog.getInstance(project)
      val supportsIndexing = log.dataManager?.logProviders?.all {
        VcsLogProperties.SUPPORTS_INDEXING.getOrDefault(it.value)
      } ?: false

      val isGraphReady = log.dataManager?.dataPack?.isFull ?: false

      val allRootsIndexed = GitRepositoryManager.getInstance(project).repositories.all {
        log.dataManager?.index?.isIndexed(it.root) ?: false
      }

      e.presentation.isEnabled = supportsIndexing && isGraphReady && allRootsIndexed
      e.presentation.description = when {
        !supportsIndexing -> {
          message("action.Git.Show.My.Branches.description.not.support.indexing")
        }
        !allRootsIndexed -> {
          message("action.Git.Show.My.Branches.description.not.all.roots.indexed")
        }
        !isGraphReady -> {
          message("action.Git.Show.My.Branches.description.not.graph.ready")
        }
        else -> {
          message("action.Git.Show.My.Branches.description.is.my.branch")
        }
      }
    }
  }

  class FetchAction(private val ui: BranchesDashboardUi) : GitFetch() {
    override fun update(e: AnActionEvent) {
      super.update(e)
      with(e.presentation) {
        text = message("action.Git.Fetch.title")
        icon = AllIcons.Vcs.Fetch
        description = ""
        val project = e.project ?: return@with
        if (GitFetchSupport.fetchSupport(project).isFetchRunning) {
          isEnabled = false
          description = message("action.Git.Fetch.description.fetch.in.progress")
        }
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      ui.startLoadingBranches()
      super.actionPerformed(e)
    }

    override fun onFetchFinished(result: GitFetchResult) {
      ui.stopLoadingBranches()
    }
  }

  class ToggleFavoriteAction : BranchesActionBase(text = messagePointer("action.Git.Toggle.Favorite.title"), icon = AllIcons.Nodes.Favorite) {
    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project!!
      val branches = e.getData(GIT_BRANCHES)!!

      val gitBranchManager = project.service<GitBranchManager>()
      for (branch in branches) {
        val type = if (branch.isLocal) GitBranchType.LOCAL else GitBranchType.REMOTE
        for (repository in branch.repositories) {
          gitBranchManager.setFavorite(type, repository, branch.branchName, !branch.isFavorite)
        }
      }
    }
  }

  class ChangeBranchFilterAction : BooleanPropertyToggleAction() {
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      super.setSelected(e, state)
      e.getRequiredData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES)[NAVIGATE_LOG_TO_BRANCH_ON_BRANCH_SELECTION_PROPERTY] = false
    }

    override fun getProperty(): VcsLogUiProperties.VcsLogUiProperty<Boolean> = CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY
  }

  class NavigateLogToBranchAction : BooleanPropertyToggleAction() {
    override fun isSelected(e: AnActionEvent): Boolean {
      return super.isSelected(e) &&
             !e.getRequiredData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES)[CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY]
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      super.setSelected(e, state)
      e.getRequiredData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES)[CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY] = false
    }

    override fun getProperty(): VcsLogUiProperties.VcsLogUiProperty<Boolean> = NAVIGATE_LOG_TO_BRANCH_ON_BRANCH_SELECTION_PROPERTY
  }

  class GroupBranchByDirectoryAction(private val tree: FilteringBranchesTree) : BranchGroupingAction(GroupingKey.GROUPING_BY_DIRECTORY,
                                                                                                     AllIcons.Actions.GroupByPackage) {
    override fun setSelected(key: GroupingKey, state: Boolean) {
      tree.toggleDirectoryGrouping(state)
    }
  }

  class HideBranchesAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
      val properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES)
      e.presentation.isEnabledAndVisible = properties != null && properties.exists(SHOW_GIT_BRANCHES_LOG_PROPERTY)
      super.update(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
      val properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES)
      if (properties != null && properties.exists(SHOW_GIT_BRANCHES_LOG_PROPERTY)) {
        properties.set(SHOW_GIT_BRANCHES_LOG_PROPERTY, false)
      }
    }
  }

  class RemoveRemoteAction(private val repository: GitRepository) : RemoteActionBase(repository) {

    override fun update(e: AnActionEvent, project: Project, remoteNames: Set<String>) {
      e.presentation.text = message("action.Git.Log.Remove.Remote.text", remoteNames.size)
    }

    override fun doAction(e: AnActionEvent, project: Project, remotes: Set<GitRemote>) {
      removeRemotes(service(), repository, remotes)
    }
  }

  class EditRemoteAction(private val repository: GitRepository) :
    RemoteActionBase(repository, messagePointer("action.Git.Log.Edit.Remote.text")) {

    override fun update(e: AnActionEvent, project: Project, remoteNames: Set<String>) {
      if (remoteNames.size != 1) {
        e.presentation.isEnabledAndVisible = false
      }
    }

    override fun doAction(e: AnActionEvent, project: Project, remotes: Set<GitRemote>) {
      editRemote(service(), repository, remotes.first())
    }
  }

  abstract class RemoteActionBase(private val repository: GitRepository,
                                  @Nls(capitalization = Nls.Capitalization.Title) text: () -> String = { "" },
                                  @Nls(capitalization = Nls.Capitalization.Sentence) private val description: () -> String = { "" },
                                  icon: Icon? = null) :
    DumbAwareAction(text, description, icon) {

    open fun update(e: AnActionEvent, project: Project, remoteNames: Set<String>) {}
    abstract fun doAction(e: AnActionEvent, project: Project, remotes: Set<GitRemote>)

    override fun update(e: AnActionEvent) {
      val project = e.project
      val remoteNames = getSelectedRemoteNames(e)
      val enabled = project != null && remoteNames.isNotEmpty() && repository.remotes.any { remoteNames.contains(it.name) }
      e.presentation.isEnabled = enabled
      e.presentation.description = description()
      if (enabled) {
        update(e, project!!, remoteNames)
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val remoteNames = getSelectedRemoteNames(e)
      val remotes = repository.remotes.filterTo(hashSetOf()) { remoteNames.contains(it.name) }

      doAction(e, project, remotes)
    }

    private fun getSelectedRemoteNames(e: AnActionEvent): Set<String> {
      val remoteNamesFromBranches =
        e.getData(GIT_BRANCHES)
          ?.asSequence()
          ?.filterNot(BranchInfo::isLocal)
          ?.mapNotNull { it.branchName.split("/").getOrNull(0) }?.toSet()
      val selectedRemoteNames = e.getData(GIT_REMOTES)
      return hashSetOf<String>().apply {
        if (selectedRemoteNames != null) addAll(selectedRemoteNames)
        if (remoteNamesFromBranches != null) addAll(remoteNamesFromBranches)
      }
    }
  }

  abstract class BranchesActionBase(@Nls(capitalization = Nls.Capitalization.Title) text: () -> String = { "" },
                                    @Nls(capitalization = Nls.Capitalization.Sentence) private val description: () -> String = { "" },
                                    icon: Icon? = null) :
    DumbAwareAction(text, description, icon) {

    open fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {}

    override fun update(e: AnActionEvent) {
      val branches = e.getData(GIT_BRANCHES)
      val project = e.project
      val enabled = project != null && branches != null && branches.isNotEmpty()
      e.presentation.isEnabled = enabled
      e.presentation.description = description()
      if (enabled) {
        update(e, project!!, branches!!)
      }
    }
  }

  class CheckoutSelectedBranchAction : BranchesActionBase() {

    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {
      if (branches.size > 1) {
        e.presentation.isEnabled = false
        return
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project!!
      val branch = e.getData(GIT_BRANCHES)!!.firstOrNull() ?: return
      if (branch.isLocal) {
        GitBranchPopupActions.LocalBranchActions.CheckoutAction
          .checkoutBranch(project, branch.repositories, branch.branchName)
      }
      else {
        GitBranchPopupActions.RemoteBranchActions.CheckoutRemoteBranchAction
          .checkoutRemoteBranch(project, branch.repositories, branch.branchName)
      }
    }
  }

  class UpdateBranchFilterInLogAction : DumbAwareAction() {

    override fun update(e: AnActionEvent) {
      val branchFilters = e.getData(GIT_BRANCH_FILTERS)
      val uiController = e.getData(BRANCHES_UI_CONTROLLER)
      val project = e.project
      val enabled = project != null && uiController != null && branchFilters != null && branchFilters.isNotEmpty()
                    && e.getData(PlatformDataKeys.CONTEXT_COMPONENT) is BranchesTreeComponent
      e.presentation.isEnabled = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.getRequiredData(BRANCHES_UI_CONTROLLER).updateLogBranchFilter()
    }
  }

  class NavigateLogToSelectedBranchAction : DumbAwareAction() {

    override fun update(e: AnActionEvent) {
      val branchFilters = e.getData(GIT_BRANCH_FILTERS)
      val uiController = e.getData(BRANCHES_UI_CONTROLLER)
      val project = e.project
      val visible = project != null && uiController != null
      if (!visible) {
        e.presentation.isEnabledAndVisible = visible
        return
      }
      val enabled = branchFilters != null && branchFilters.size == 1

      e.presentation.isEnabled = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.getRequiredData(BRANCHES_UI_CONTROLLER).navigateLogToSelectedBranch()
    }
  }

  class RenameLocalBranch : BranchesActionBase() {

    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {
      if (branches.size > 1) {
        e.presentation.isEnabled = false
        return
      }
      val branch = branches.firstOrNull()
      if (branch == null || !branch.isLocal || branch.repositories.any(Repository::isFresh)) {
        e.presentation.isEnabled = false
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project!!
      val branch = e.getData(GIT_BRANCHES)!!.firstOrNull() ?: return
      GitBranchPopupActions.LocalBranchActions.RenameBranchAction.rename(project, branch.repositories, branch.branchName)
    }
  }
}
