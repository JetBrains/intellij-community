// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil.disableActionIfAnyRepositoryIsFresh
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.dvcs.getCommonCurrentBranch
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationBundle
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
import git4idea.GitRemoteBranch
import git4idea.actions.GitFetch
import git4idea.actions.branch.GitBranchActionsUtil.calculateNewBranchInitialName
import git4idea.branch.GitBranchType
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitBrancher
import git4idea.branch.GitRefType
import git4idea.branch.IncomingOutgoingState
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
import org.jetbrains.annotations.Nls
import java.util.function.Supplier
import javax.swing.Icon

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

  internal class HeadAndBranchActions() : ActionGroup(), DumbAware {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      arrayOf(ShowArbitraryBranchesDiffAction(), ShowArbitraryBranchesFileDiffAction())
  }

  class MultipleLocalBranchActions : ActionGroup(), DumbAware {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      arrayOf(ShowArbitraryBranchesDiffAction(), ShowArbitraryBranchesFileDiffAction(), UpdateSelectedBranchAction(), DeleteBranchAction())
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
      val selection = e?.getData(GIT_BRANCHES_TREE_SELECTION) ?: return null

      val selectedRefs = selection.selectedRefs
      val headSelected = selection.headSelected
      val selectedRemotes = selection.selectedRemotes
      val selectedNodes = selection.selectedNodeDescriptors

      return when {
        selectedNodes.size == 1 && (selectedRefs.size == 1 || headSelected) ->
          ActionManager.getInstance().getAction(GIT_SINGLE_REF_ACTION_GROUP) as? ActionGroup
        selectedNodes.size == 2 && selectedRefs.size == 1 && headSelected -> HeadAndBranchActions()
        selectedNodes.size == selectedRefs.size && selectedRefs.size > 1 -> MultipleLocalBranchActions()
        selectedNodes.isNotEmpty() && selectedRemotes.size == selectedNodes.size ->
          if (selectedRemotes.size == 1) GroupActions() else MultipleGroupActions()
        (selectedNodes.singleOrNull() as? BranchNodeDescriptor.TopLevelGroup)?.refType == GitBranchType.REMOTE ->
          RemoteGlobalActions()
        else -> null
      }
    }
  }

  class NewBranchAction : BranchesActionBase({ DvcsBundle.message("new.branch.action.text") },
                                             { DvcsBundle.message("new.branch.action.text") },
                                             com.intellij.dvcs.ui.NewBranchAction.icon) {

    override fun update(e: AnActionEvent) {
      val headSelected = e.getData(GIT_BRANCHES_TREE_SELECTION)?.headSelected
      if (headSelected == true) {
        e.presentation.isEnabled = true
      }
      else {
        super.update(e)
      }
    }

    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>, selection: BranchesTreeSelection) {
      if (branches.size > 1) {
        e.presentation.isEnabled = false
        e.presentation.description = message("action.Git.New.Branch.description")
        return
      }

      val repositories = selection.repositoriesOfSelectedBranches
      disableActionIfAnyRepositoryIsFresh(e, repositories, DvcsBundle.message("action.not.possible.in.fresh.repo.new.branch"))
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val selection = e.getData(GIT_BRANCHES_TREE_SELECTION) ?: return

      if (selection.headSelected) {
        val repositories = GitRepositoryManager.getInstance(project).repositories
        createOrCheckoutNewBranch(project, repositories, HEAD, initialName = repositories.getCommonCurrentBranch())
      }
      else {
        val branches = selection.selectedBranches
        val repositories = selection.repositoriesOfSelectedBranches
        val branchInfo = branches.first()
        val branchName = branchInfo.branchName

        createOrCheckoutNewBranch(project, repositories, "$branchName^0",
                                  message("action.Git.New.Branch.dialog.title", branchName),
                                  calculateNewBranchInitialName(branchName, !branchInfo.isLocalBranch))
      }
    }

  }

  class UpdateSelectedBranchAction : BranchesActionBase(text = messagePointer("action.Git.Update.Selected.text"),
                                                        icon = AllIcons.Actions.CheckOut) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      val enabledAndVisible = e.project?.let(::hasRemotes) ?: false
      e.presentation.isEnabledAndVisible = enabledAndVisible

      if (enabledAndVisible) {
        super.update(e)
      }
    }

    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>, selection: BranchesTreeSelection) {
      val presentation = e.presentation
      if (GitFetchSupport.fetchSupport(project).isFetchRunning) {
        presentation.isEnabled = false
        presentation.description = message("action.Git.Update.Selected.description.already.running")
        return
      }

      val selectedRepositories = selection.repositoriesOfSelectedBranches

      val branchNames = branches.map(BranchInfo::branchName)
      val updateMethodName = GitVcsSettings.getInstance(project).updateMethod.name.toLowerCase()
      presentation.description = message("action.Git.Update.Selected.description", branches.size, updateMethodName)
      val trackingInfosExist = isTrackingInfosExist(branchNames, selectedRepositories)
      presentation.isEnabled = trackingInfosExist
      if (!trackingInfosExist) {
        presentation.description = message("action.Git.Update.Selected.description.tracking.not.configured", branches.size)
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val selection = e.getData(GIT_BRANCHES_TREE_SELECTION) ?: return

      val branches = selection.selectedBranches
      val repositories = selection.repositoriesOfSelectedBranches
      val branchNames = branches.map(BranchInfo::branchName)

      updateBranches(project, repositories, branchNames)
    }
  }

  class DeleteBranchAction : RefActionBase(
    text = messagePointer("action.Git.Delete.Branch.title", 0),
    icon = AllIcons.Actions.GC
  ) {
    init {
      shortcutSet = CompositeShortcutSet(KeymapUtil.getActiveKeymapShortcuts("SafeDelete"),
                                         KeymapUtil.getActiveKeymapShortcuts("EditorDeleteToLineStart"))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent, project: Project, refs: Collection<RefInfo>, selection: BranchesTreeSelection) {
      val allRefsAreBranches = refs.all { it is BranchInfo}

      e.presentation.text =
        if (allRefsAreBranches) message("action.Git.Delete.Branch.title", refs.size)
        else ApplicationBundle.message("button.delete")

      val enabled = refs.none { refInfo ->
        val remoteBranchInfo = (refInfo as? BranchInfo)?.branch as? GitRemoteBranch
        refInfo.isCurrent ||
        (remoteBranchInfo != null && isRemoteBranchProtected(selection.getSelectedRepositories(refInfo), remoteBranchInfo.name))
      }

      e.presentation.isEnabled = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val selection = e.getData(GIT_BRANCHES_TREE_SELECTION) ?: return

      delete(project, selection)
    }

    private fun delete(project: Project, selection: BranchesTreeSelection) {
      val gitBrancher = GitBrancher.getInstance(project)

      val remoteBranchesNames = mutableSetOf<String>()
      val remoteBranchesRepos = mutableSetOf<GitRepository>()
      val localBranches = mutableMapOf<String, List<GitRepository>>()
      val tags = mutableMapOf<String, List<GitRepository>>()
      selection.selectedRefsToRepositories.forEach { (refInfo, repos) ->
        when (refInfo) {
          is BranchInfo -> {
            if (refInfo.isLocalBranch) {
              localBranches[refInfo.branchName] = repos
            }
            else {
              remoteBranchesNames.add(refInfo.branchName)
              remoteBranchesRepos.addAll(repos)
            }
          }
          is TagInfo -> {
            tags[refInfo.ref.name] = repos
          }
        }
      }

      val deleteRemoteBranches = {
        gitBrancher.deleteRemoteBranches(remoteBranchesNames.toList(), remoteBranchesRepos.toList())
      }

      if (tags.isNotEmpty()) {
        gitBrancher.deleteTags(tags)
      }
      if (localBranches.isNotEmpty()) { //delete local (possible tracked) branches first if any
        gitBrancher.deleteBranches(localBranches, deleteRemoteBranches)
      }
      else {
        deleteRemoteBranches()
      }
    }
  }

  class ShowBranchDiffAction : BranchesActionBase(text = messagePointer("action.Git.Compare.With.Current.title"),
                                                  icon = AllIcons.Actions.Diff) {
    init {
      shortcutSet = KeymapUtil.getActiveKeymapShortcuts("Diff.ShowDiff")
    }

    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>, selection: BranchesTreeSelection) {
      if (branches.none { !it.isCurrent }) {
        e.presentation.isEnabled = false
        e.presentation.description = message("action.Git.Update.Selected.description.select.non.current")
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val selection = e.getData(GIT_BRANCHES_TREE_SELECTION) ?: return
      val gitBrancher = GitBrancher.getInstance(project)

      selection.selectedRefsToRepositories.forEach { (refInfo, repositories) ->
        if (refInfo is BranchInfo && !refInfo.isCurrent) {
          gitBrancher.compare(refInfo.branchName, repositories)
        }
      }
    }
  }

  internal abstract class BranchesPairActionBase(text: () -> @Nls(capitalization = Nls.Capitalization.Title) String = { "" },
                                                 description: @Nls(capitalization = Nls.Capitalization.Sentence) () -> String = { "" },
                                                 icon: Icon? = null
  ) : BranchesActionBase(text = text, description = description, icon = icon) {
    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>, selection: BranchesTreeSelection) {
      val branchPair = getBranchPair(selection, project, e)
      if (branchPair == null) {
        e.presentation.isEnabledAndVisible = false
        e.presentation.description = ""
      }
      else {
        val (branchOne, branchTwo) = branchPair
        if (branchOne.branchName == branchTwo.branchName || commonRepositories(branchOne, branchTwo, selection).isEmpty()) {
          e.presentation.isEnabled = false
          e.presentation.description = message("action.Git.Compare.Selected.description.disabled")
        }
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val selection = e.getData(GIT_BRANCHES_TREE_SELECTION) ?: return
      val (branchOne, branchTwo) = getBranchPair(selection, project, e) ?: return

      val commonRepositories = commonRepositories(branchOne, branchTwo, selection)

      performAction(project, branchOne, branchTwo, commonRepositories)
    }

    private fun getBranchPair(selection: BranchesTreeSelection, project: Project, e: AnActionEvent): Pair<BranchInfo, BranchInfo>? {
      val branches = selection.selectedBranches
      return when {
        branches.size == 1 && selection.headSelected -> {
          val guessRepo = GitBranchUtil.guessWidgetRepository(project, e.dataContext)
                          ?: branches.single().repositories.singleOrNull()
                          ?: return null
          val currentBranch = guessRepo.currentBranch ?: return null
          branches.single() to BranchInfo(currentBranch, true, false, IncomingOutgoingState.EMPTY, listOf(guessRepo))
        }
        branches.size == 2 -> {
          branches[0] to branches[1]
        }
        else -> null
      }
    }

    private fun commonRepositories(branchOne: BranchInfo, branchTwo: BranchInfo, selection: BranchesTreeSelection): Collection<GitRepository> {
      return if (branchOne.repositories.size == 1 && branchTwo.repositories.size == 1)
        branchOne.repositories intersect branchTwo.repositories
      else
        selection.getSelectedRepositories(branchOne) intersect selection.getSelectedRepositories(branchTwo)
    }

    abstract fun performAction(project: Project,
                               branchOne: BranchInfo,
                               branchTwo: BranchInfo,
                               commonRepositories: Collection<GitRepository>)
  }

  class ShowArbitraryBranchesDiffAction() :
    BranchesPairActionBase(text = messagePointer("action.Git.Compare.Selected.title"),
                           description = messagePointer("action.Git.Compare.Selected.description"),
                           icon = AllIcons.Actions.Diff) {

    override fun performAction(project: Project,
                               branchOne: BranchInfo,
                               branchTwo: BranchInfo,
                               commonRepositories: Collection<GitRepository>) {
      GitBrancher.getInstance(project).compareAny(branchOne.branchName, branchTwo.branchName, commonRepositories.toList())
    }
  }

  class ShowArbitraryBranchesFileDiffAction() :
    BranchesPairActionBase(text = messagePointer("action.Git.Compare.Selected.Heads.title"),
                           description = messagePointer("action.Git.Compare.Selected.Heads.description"), null) {

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

  class ToggleFavoriteAction : RefActionBase(text = messagePointer("action.Git.Toggle.Favorite.title"), icon = AllIcons.Nodes.Favorite) {
    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val selection = e.getData(GIT_BRANCHES_TREE_SELECTION) ?: return

      val gitBranchManager = project.service<GitBranchManager>()
      selection.selectedRefsToRepositories.forEach { (refInfo, repositories) ->
        val type = GitRefType.of(refInfo.ref)
        for (repository in repositories) {
          gitBranchManager.setFavorite(type, repository, refInfo.refName, !refInfo.isFavorite)
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
        properties[SHOW_GIT_BRANCHES_LOG_PROPERTY] = false
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

  abstract class RefActionBase(text: () -> @Nls(capitalization = Nls.Capitalization.Title) String, icon: Icon) :
    DumbAwareAction(text, icon) {

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
      val selection = e.getData(GIT_BRANCHES_TREE_SELECTION)
      val selectedRefs = selection?.selectedRefs
      val project = e.project
      val enabled = project != null && !selectedRefs.isNullOrEmpty()
      e.presentation.isEnabled = enabled
      if (enabled) {
        update(e, project, selectedRefs, selection)
      }
    }

    open fun update(e: AnActionEvent, project: Project, refs: Collection<RefInfo>, selection: BranchesTreeSelection) {}
  }

  abstract class BranchesActionBase(text: () -> @Nls(capitalization = Nls.Capitalization.Title) String = { "" },
                                    private val description: () -> @Nls(capitalization = Nls.Capitalization.Sentence) String = { "" },
                                    icon: Icon? = null) :
    DumbAwareAction(text, description, icon) {

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    open fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>, selection: BranchesTreeSelection) {}

    override fun update(e: AnActionEvent) {
      val selection = e.getData(GIT_BRANCHES_TREE_SELECTION)
      val branches = selection?.selectedBranches
      val project = e.project
      val enabled = project != null && !branches.isNullOrEmpty()
      e.presentation.isEnabled = enabled
      e.presentation.description = description()
      if (enabled) {
        update(e, project, branches, selection)
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
                    && !e.getData(GIT_BRANCHES_TREE_SELECTION)?.selectedBranchFilters.isNullOrEmpty()
                    && e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) is BranchesTreeComponent

      e.presentation.isEnabled = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
      val controller = e.getData(BRANCHES_UI_CONTROLLER) ?: return
      controller.updateLogBranchFilter()
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

      e.presentation.isEnabled = e.getData(GIT_BRANCHES_TREE_SELECTION)?.logNavigatableNodeDescriptor != null
      e.presentation.isVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
      val controller = e.getData(BRANCHES_UI_CONTROLLER) ?: return
      val selection = e.getData(GIT_BRANCHES_TREE_SELECTION)?.logNavigatableNodeDescriptor ?: return
      controller.navigateLogToRef(selection)
    }
  }
}
