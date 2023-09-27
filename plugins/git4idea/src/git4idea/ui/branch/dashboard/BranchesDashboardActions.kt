// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil.disableActionIfAnyRepositoryIsFresh
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.dvcs.diverged
import com.intellij.dvcs.getCommonCurrentBranch
import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEMS
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.VcsLogProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.actions.BooleanPropertyToggleAction
import com.intellij.vcs.log.util.VcsLogUtil.HEAD
import git4idea.actions.GitFetch
import git4idea.actions.branch.GitBranchActionsUtil.calculateNewBranchInitialName
import git4idea.branch.GitBranchType
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitBrancher
import git4idea.commands.Git
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
import git4idea.ui.branch.dashboard.BranchesTreeComponent.Companion.getSelectedRepositories
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.tree.TreePath

internal object BranchesDashboardActions {

  class BranchesTreeActionGroup : ActionGroup(), DumbAware {

    init {
      templatePresentation.isPopupGroup = true
      templatePresentation.isHideGroupIfEmpty = true
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      BranchActionsBuilder.build(e)?.getChildren(e) ?: AnAction.EMPTY_ARRAY

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  class MultipleLocalBranchActions : ActionGroup(), DumbAware {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      arrayOf(ShowArbitraryBranchesDiffAction(), ShowArbitraryBranchesFileDiffAction(), UpdateSelectedBranchAction(), DeleteBranchAction())
  }

  class CurrentBranchActions(project: Project,
                             repositories: List<GitRepository>,
                             branchName: String,
                             selectedRepository: GitRepository)
    : GitBranchPopupActions.CurrentBranchActions(project, repositories, branchName, selectedRepository) {

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
                           selectedRepository: GitRepository)
    : GitBranchPopupActions.LocalBranchActions(project, repositories, branchName, selectedRepository) {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      arrayListOf<AnAction>(*super.getChildren(e)).toTypedArray()
  }

  class RemoteBranchActions(project: Project,
                            repositories: List<GitRepository>,
                            @NonNls branchName: String,
                            selectedRepository: GitRepository)
    : GitBranchPopupActions.RemoteBranchActions(project, repositories, branchName, selectedRepository) {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      arrayListOf<AnAction>(*super.getChildren(e)).toTypedArray()
  }

  class GroupActions : ActionGroup(), DumbAware {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      arrayListOf<AnAction>(EditRemoteAction(), RemoveRemoteAction()).toTypedArray()
  }

  class MultipleGroupActions : ActionGroup(), DumbAware {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      arrayListOf<AnAction>(RemoveRemoteAction()).toTypedArray()
  }

  class RemoteGlobalActions : ActionGroup(), DumbAware {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      arrayListOf<AnAction>(ActionManager.getInstance().getAction("Git.Configure.Remotes")).toTypedArray()
  }

  object BranchActionsBuilder {

    @RequiresBackgroundThread
    fun build(e: AnActionEvent?): ActionGroup? {
      val project = e?.project ?: return null
      val selectedBranches = e.getData(GIT_BRANCHES).orEmpty()
      val multipleBranchSelection = selectedBranches.size > 1
      val guessRepo = GitBranchUtil.guessWidgetRepository(project, e.dataContext) ?: return null
      if (multipleBranchSelection) {
        return MultipleLocalBranchActions()
      }

      val branchInfo = selectedBranches.singleOrNull()
      val headSelected = e.getData(GIT_BRANCH_FILTERS).orEmpty().contains(HEAD)
      if (branchInfo != null && !headSelected) {
        @Suppress("UNCHECKED_CAST")
        val selectionPaths = e.getData(SELECTED_ITEMS) as? Array<TreePath>
        val selectedRepositories = getSelectedRepositories(branchInfo, selectionPaths).toList().ifEmpty(branchInfo::repositories)
        val selectedRepository = selectedRepositories.singleOrNull() ?: guessRepo

        return when {
          branchInfo.isCurrent -> CurrentBranchActions(project, selectedRepositories, branchInfo.branchName, selectedRepository)
          branchInfo.isLocal -> LocalBranchActions(project, selectedRepositories, branchInfo.branchName, selectedRepository)
          else -> RemoteBranchActions(project, selectedRepositories, branchInfo.branchName, selectedRepository)
        }
      }

      val selectedRemotes = e.getData(GIT_BRANCH_REMOTES).orEmpty()
      if (selectedRemotes.size == 1) {
        return GroupActions()
      }
      else if (selectedRemotes.isNotEmpty()) {
        return MultipleGroupActions()
      }

      val selectedBranchNodes = e.getData(GIT_BRANCH_DESCRIPTORS).orEmpty()
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

      val controller = e.getData(BRANCHES_UI_CONTROLLER)!!
      val repositories = branches.flatMap(controller::getSelectedRepositories).distinct()

      disableActionIfAnyRepositoryIsFresh(e, repositories, DvcsBundle.message("action.not.possible.in.fresh.repo.new.branch"))
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project!!
      val branchFilters = e.getData(GIT_BRANCH_FILTERS)
      if (branchFilters != null && branchFilters.contains(HEAD)) {
        val repositories = GitRepositoryManager.getInstance(project).repositories

        createOrCheckoutNewBranch(project, repositories, HEAD, initialName = repositories.getCommonCurrentBranch())
      }
      else {
        val branches = e.getData(GIT_BRANCHES)!!
        val controller = e.getData(BRANCHES_UI_CONTROLLER)!!
        val repositories = branches.flatMap(controller::getSelectedRepositories).distinct()
        val branchInfo = branches.first()
        val branchName = branchInfo.branchName

        createOrCheckoutNewBranch(project, repositories, "$branchName^0",
                                  message("action.Git.New.Branch.dialog.title", branchName),
                                  calculateNewBranchInitialName(branchName, !branchInfo.isLocal))
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
      val controller = e.getData(BRANCHES_UI_CONTROLLER)!!
      val repositories = branches.flatMap(controller::getSelectedRepositories).distinct()
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
      val controller = e.getData(BRANCHES_UI_CONTROLLER)!!
      val repositories = branches.flatMap(controller::getSelectedRepositories).distinct()
      val branchNames = branches.map(BranchInfo::branchName)

      updateBranches(project, repositories, branchNames)
    }
  }

  class DeleteBranchAction : BranchesActionBase(icon = AllIcons.Actions.GC) {
    init {
      shortcutSet = CompositeShortcutSet(KeymapUtil.getActiveKeymapShortcuts("SafeDelete"),
                                         KeymapUtil.getActiveKeymapShortcuts("EditorDeleteToLineStart"))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {
      e.presentation.text = message("action.Git.Delete.Branch.title", branches.size)

      @Suppress("UNCHECKED_CAST")
      val selectionPaths = e.getData(SELECTED_ITEMS) as? Array<TreePath>

      val disabled =
        branches.any {
          it.isCurrent || (!it.isLocal && isRemoteBranchProtected(getSelectedRepositories(it, selectionPaths), it.branchName))
        }

      e.presentation.isEnabled = !disabled
    }

    override fun actionPerformed(e: AnActionEvent) {
      val branches = e.getData(GIT_BRANCHES)!!
      val project = e.project!!
      val controller = e.getData(BRANCHES_UI_CONTROLLER)!!

      delete(project, branches, controller)
    }

    private fun delete(project: Project, branches: Collection<BranchInfo>, controller: BranchesDashboardController) {
      val gitBrancher = GitBrancher.getInstance(project)
      val (localBranches, remoteBranches) = branches.partition { it.isLocal && !it.isCurrent }
      with(gitBrancher) {
        val branchesToContainingRepositories: Map<String, List<GitRepository>> =
          localBranches.associate { it.branchName to controller.getSelectedRepositories(it) }

        val deleteRemoteBranches = {
          deleteRemoteBranches(remoteBranches.map(BranchInfo::branchName), remoteBranches.flatMap(BranchInfo::repositories).distinct())
        }

        val localBranchNames = branchesToContainingRepositories.keys
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
    init {
      shortcutSet = KeymapUtil.getActiveKeymapShortcuts("Diff.ShowDiff")
    }

    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {
      if (branches.none { !it.isCurrent }) {
        e.presentation.isEnabled = false
        e.presentation.description = message("action.Git.Update.Selected.description.select.non.current")
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val controller = e.getData(BRANCHES_UI_CONTROLLER)!!
      val branches = e.getData(GIT_BRANCHES)!!
      val project = e.project!!
      val gitBrancher = GitBrancher.getInstance(project)

      for (branch in branches.filterNot(BranchInfo::isCurrent)) {
        gitBrancher.compare(branch.branchName, controller.getSelectedRepositories(branch))
      }
    }
  }

  internal abstract class BranchesPairActionBase(text: () -> @Nls(capitalization = Nls.Capitalization.Title) String = { "" },
                                                 description: @Nls(capitalization = Nls.Capitalization.Sentence) () -> String = { "" },
                                                 icon: Icon? = null) : BranchesActionBase(text = text, description = description,
                                                                                          icon = icon) {
    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {
      if (branches.size != 2) {
        e.presentation.isEnabledAndVisible = false
        e.presentation.description = ""
      }
      else {
        val branchOne = branches.elementAt(0)
        val branchTwo = branches.elementAt(1)
        val controller = e.getData(BRANCHES_UI_CONTROLLER)!!

        if (branchOne.branchName == branchTwo.branchName || controller.commonRepositories(branchOne, branchTwo).isEmpty()) {
          e.presentation.isEnabled = false
          e.presentation.description = message("action.Git.Compare.Selected.description.disabled")
        }
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val controller = e.getData(BRANCHES_UI_CONTROLLER)!!
      val branches = e.getData(GIT_BRANCHES)!!
      val branchOne = branches.elementAt(0)
      val branchTwo = branches.elementAt(1)
      val commonRepositories = controller.commonRepositories(branchOne, branchTwo)

      performAction(e.project!!, branchOne, branchTwo, commonRepositories)
    }

    private fun BranchesDashboardController.commonRepositories(branchOne: BranchInfo, branchTwo: BranchInfo): Collection<GitRepository> {
      return getSelectedRepositories(branchOne) intersect getSelectedRepositories(branchTwo)
    }

    abstract fun performAction(project: Project,
                               branchOne: BranchInfo,
                               branchTwo: BranchInfo,
                               commonRepositories: Collection<GitRepository>)
  }

  class ShowArbitraryBranchesDiffAction : BranchesPairActionBase(text = messagePointer("action.Git.Compare.Selected.title"),
                                                                 description = messagePointer("action.Git.Compare.Selected.description"),
                                                                 icon = AllIcons.Actions.Diff) {
    override fun performAction(project: Project,
                               branchOne: BranchInfo,
                               branchTwo: BranchInfo,
                               commonRepositories: Collection<GitRepository>) {
      GitBrancher.getInstance(project).compareAny(branchOne.branchName, branchTwo.branchName, commonRepositories.toList())
    }
  }

  class ShowArbitraryBranchesFileDiffAction : BranchesPairActionBase(text = messagePointer("action.Git.Compare.Selected.Heads.title"),
                                                                     description = messagePointer("action.Git.Compare.Selected.Heads.description")) {
    override fun performAction(project: Project,
                               branchOne: BranchInfo,
                               branchTwo: BranchInfo,
                               commonRepositories: Collection<GitRepository>) {
      GitBrancher.getInstance(project).showDiff(branchOne.branchName, branchTwo.branchName, commonRepositories.toList())
    }
  }

  class ShowMyBranchesAction(private val uiController: BranchesDashboardController)
    : ToggleAction(messagePointer("action.Git.Show.My.Branches.title"), AllIcons.Actions.Find), DumbAware {

    override fun isSelected(e: AnActionEvent) = uiController.showOnlyMy

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      uiController.showOnlyMy = state
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
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
      e.presentation.text = message("action.Git.Fetch.title")
      e.presentation.icon = AllIcons.Vcs.Fetch
    }

    override fun actionPerformed(e: AnActionEvent) {
      ui.startLoadingBranches()
      super.actionPerformed(e)
    }

    override fun onFetchFinished(project: Project, result: GitFetchResult) {
      ui.stopLoadingBranches()
    }
  }

  class ToggleFavoriteAction : BranchesActionBase(text = messagePointer("action.Git.Toggle.Favorite.title"),
                                                  icon = AllIcons.Nodes.Favorite) {
    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project!!
      val branches = e.getData(GIT_BRANCHES)!!

      val gitBranchManager = project.service<GitBranchManager>()
      for (branch in branches) {
        val type = if (branch.isLocal) GitBranchType.LOCAL else GitBranchType.REMOTE
        val controller = e.getData(BRANCHES_UI_CONTROLLER)!!
        val repositories = controller.getSelectedRepositories(branch)

        for (repository in repositories) {
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

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
  }

  class GroupingSettingsGroup : DefaultActionGroup(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isPopupGroup = GroupBranchByRepositoryAction.isEnabledAndVisible(e)
    }
  }

  class GroupBranchByDirectoryAction : BranchGroupingAction(GroupingKey.GROUPING_BY_DIRECTORY) {
    override fun update(e: AnActionEvent) {
      super.update(e)

      val groupByDirectory: Supplier<String> = DvcsBundle.messagePointer("action.text.branch.group.by.directory")
      val groupingSeparator: () -> String = messagePointer("group.Git.Log.Branches.Grouping.Settings.text")

      e.presentation.text =
        if (GroupBranchByRepositoryAction.isEnabledAndVisible(e)) groupByDirectory.get() //NON-NLS
        else groupingSeparator() + " " + groupByDirectory.get() //NON-NLS
    }
  }

  class GroupBranchByRepositoryAction : BranchGroupingAction(GroupingKey.GROUPING_BY_REPOSITORY) {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabledAndVisible = isEnabledAndVisible(e)
    }

    companion object {
      fun isEnabledAndVisible(e: AnActionEvent): Boolean =
        e.project?.let(RepositoryChangesBrowserNode.Companion::getColorManager)?.hasMultiplePaths() ?: false
    }
  }

  class HideBranchesAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

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

  class RemoveRemoteAction : RemoteActionBase(messagePointer("action.Git.Log.Remove.Remote.text", 0)) {

    override fun update(e: AnActionEvent, project: Project, selectedRemotes: Map<GitRepository, Set<GitRemote>>) {
      e.presentation.text = message("action.Git.Log.Remove.Remote.text", selectedRemotes.size)
    }

    override fun doAction(e: AnActionEvent, project: Project, selectedRemotes: Map<GitRepository, Set<GitRemote>>) {
      for ((repository, remotes) in selectedRemotes) {
        removeRemotes(Git.getInstance(), repository, remotes)
      }
    }
  }

  class EditRemoteAction : RemoteActionBase(messagePointer("action.Git.Log.Edit.Remote.text")) {

    override fun update(e: AnActionEvent, project: Project, selectedRemotes: Map<GitRepository, Set<GitRemote>>) {
      if (selectedRemotes.size != 1) {
        e.presentation.isEnabledAndVisible = false
      }
    }

    override fun doAction(e: AnActionEvent, project: Project, selectedRemotes: Map<GitRepository, Set<GitRemote>>) {
      val (repository, remotes) = selectedRemotes.entries.first()
      editRemote(Git.getInstance(), repository, remotes.first())
    }
  }

  abstract class RemoteActionBase(text: () -> @Nls(capitalization = Nls.Capitalization.Title) String,
                                  private val description: () -> @Nls(capitalization = Nls.Capitalization.Sentence) String = { "" },
                                  icon: Icon? = null) :
    DumbAwareAction(text, description, icon) {

    open fun update(e: AnActionEvent, project: Project, selectedRemotes: Map<GitRepository, Set<GitRemote>>) {}
    abstract fun doAction(e: AnActionEvent, project: Project, selectedRemotes: Map<GitRepository, Set<GitRemote>>)

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
      val project = e.project
      val controller = e.getData(BRANCHES_UI_CONTROLLER)
      val selectedRemotes = controller?.getSelectedRemotes() ?: emptyMap()
      val enabled = project != null && selectedRemotes.isNotEmpty()
      e.presentation.isEnabled = enabled
      e.presentation.description = description()
      if (enabled) {
        update(e, project!!, selectedRemotes)
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val controller = e.getData(BRANCHES_UI_CONTROLLER)!!
      val selectedRemotes = controller.getSelectedRemotes()

      doAction(e, project, selectedRemotes)
    }
  }

  abstract class BranchesActionBase(text: () -> @Nls(capitalization = Nls.Capitalization.Title) String = { "" },
                                    private val description: () -> @Nls(capitalization = Nls.Capitalization.Sentence) String = { "" },
                                    icon: Icon? = null) :
    DumbAwareAction(text, description, icon) {

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    open fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {}

    override fun update(e: AnActionEvent) {
      val controller = e.getData(BRANCHES_UI_CONTROLLER)
      val branches = e.getData(GIT_BRANCHES)
      val project = e.project
      val enabled = project != null && controller != null && !branches.isNullOrEmpty()
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
      val controller = e.getData(BRANCHES_UI_CONTROLLER)!!
      val repositories = controller.getSelectedRepositories(branch)

      if (branch.isLocal) {
        GitBranchPopupActions.LocalBranchActions.CheckoutAction
          .checkoutBranch(project, repositories, branch.branchName)
      }
      else {
        GitBranchPopupActions.RemoteBranchActions.CheckoutRemoteBranchAction
          .checkoutRemoteBranch(project, repositories, branch.branchName)
      }
    }
  }

  class UpdateBranchFilterInLogAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
      val enabled = e.project != null
                    && e.getData(BRANCHES_UI_CONTROLLER) != null
                    && !e.getData(GIT_BRANCH_FILTERS).isNullOrEmpty()
                    && e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) is BranchesTreeComponent

      e.presentation.isEnabled = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.getRequiredData(BRANCHES_UI_CONTROLLER).updateLogBranchFilter()
    }
  }

  class NavigateLogToSelectedBranchAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
      val uiController = e.getData(BRANCHES_UI_CONTROLLER)
      val project = e.project
      val visible = project != null && uiController != null
      if (!visible) {
        e.presentation.isEnabledAndVisible = false
        return
      }

      val branchFilters = e.getData(GIT_BRANCH_FILTERS)
      e.presentation.isEnabled = branchFilters != null && branchFilters.size == 1
      e.presentation.isVisible = true
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
      val branch = branches.first()
      val controller = e.getData(BRANCHES_UI_CONTROLLER)!!
      val repositories = controller.getSelectedRepositories(branch)

      if (!branch.isLocal || repositories.any(Repository::isFresh)) {
        e.presentation.isEnabled = false
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project!!
      val branch = e.getData(GIT_BRANCHES)!!.firstOrNull() ?: return
      val controller = e.getData(BRANCHES_UI_CONTROLLER)!!
      val repositories = controller.getSelectedRepositories(branch)

      GitBranchPopupActions.LocalBranchActions.RenameBranchAction.rename(project, repositories, branch.branchName)
    }
  }
}
