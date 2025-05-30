// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.widget.popup

import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.PopupStep.FINAL_CHOICE
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.vcs.git.shared.GitDisplayName
import com.intellij.vcs.git.shared.actions.GitSingleRefActions
import com.intellij.vcs.git.shared.ref.GitRefUtil
import com.intellij.vcs.git.shared.repo.GitRepositoryFrontendModel
import com.intellij.vcs.git.shared.widget.actions.GitBranchesWidgetActions
import com.intellij.vcs.git.shared.widget.actions.GitBranchesWidgetKeys
import com.intellij.vcs.git.shared.widget.tree.*
import git4idea.GitReference
import git4idea.config.GitVcsSettings
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class GitBranchesTreePopupStep private constructor(
  project: Project,
  selectedRepository: GitRepositoryFrontendModel?,
  repositories: List<GitRepositoryFrontendModel>,
  private val isFirstStep: Boolean,
) : GitBranchesTreePopupStepBase(project, selectedRepository, repositories) {
  private var finalRunnable: Runnable? = null

  private val topLevelItems: List<Any> = buildList {
    val presentationFactory = PresentationFactory()

    val dataContext = createDataContext(project, selectedRepository, repositories)

    if (ExperimentalUI.isNewUI() && isFirstStep) {
      val actions = GitBranchesWidgetActions.createTopLevelActionItems(dataContext, GitBranchesWidgetActions.NEW_UI_TOP_LEVEL_ACTIONS_ACTION_GROUP, presentationFactory)
      if (actions.isNotEmpty()) {
        addAll(actions)
        add(GitBranchesTreePopupBase.createTreeSeparator())
      }
    }

    val actions = GitBranchesWidgetActions.createTopLevelActionItems(dataContext, GitBranchesWidgetActions.TOP_LEVEL_ACTIONS_ACTION_GROUP, presentationFactory)
    if (actions.isNotEmpty()) {
      addAll(actions)
      add(GitBranchesTreePopupBase.createTreeSeparator())
    }
  }

  override var treeModel: GitBranchesTreeModel = createTreeModel(false)
    private set

  override fun createTreeModel(filterActive: Boolean): GitBranchesTreeModel {
    val model = when {
      !filterActive && repositories.size > 1 && !GitVcsSettings.getInstance(project).shouldExecuteOperationsOnAllRoots() && selectedRepository != null -> {
        GitBranchesTreeSelectedRepoModel(project, selectedRepository, repositories, topLevelItems)
      }
      filterActive && repositories.size > 1 -> {
        GitBranchesTreeMultiRepoFilteringModel(project, repositories, topLevelItems)
      }
      !filterActive && repositories.size > 1 -> GitBranchesTreeMultiRepoModel(project, repositories, topLevelItems)
      else -> GitBranchesTreeSingleRepoModel(project, repositories.first(), topLevelItems)
    }
    return model.apply(GitBranchesTreeModel::init)
  }

  override fun setTreeModel(treeModel: GitBranchesTreeModel) {
    this.treeModel = treeModel
  }

  override fun getFinalRunnable() = finalRunnable

  override fun onChosen(selectedValue: Any?, finalChoice: Boolean): PopupStep<out Any>? {
    if (selectedValue is GitBranchesTreeModel.RepositoryNode) {
      return createPopupStepForSelectedRepo(project, selectedValue.repository)
    }

    val refUnderRepository = selectedValue as? GitBranchesTreeModel.RefUnderRepository
    val reference = selectedValue as? GitReference ?: refUnderRepository?.ref

    if (reference != null) {
      val actionGroup = GitSingleRefActions.getSingleRefActionGroup()
      val repo = refUnderRepository?.repository
      return createActionStep(actionGroup, project, selectedRepository, repo?.let(::listOf) ?: affectedRepositories, reference)
    }

    if (selectedValue is PopupFactoryImpl.ActionItem) {
      if (!selectedValue.isEnabled) return FINAL_CHOICE
      val action = selectedValue.action
      if (action is ActionGroup && (!finalChoice || !selectedValue.isPerformGroup)) {
        return createActionStep(action, project, selectedRepository, affectedRepositories)
      }
      else {
        finalRunnable = Runnable {
          val place = if (isFirstStep) GitBranchesWidgetActions.MAIN_POPUP_ACTION_PLACE else GitBranchesWidgetActions.NESTED_POPUP_ACTION_PLACE
          val dataContext = createDataContext(project, selectedRepository, affectedRepositories)
          ActionUtil.invokeAction(action, dataContext, place, null, null)
        }
      }
    }

    return FINAL_CHOICE
  }

  override fun getTitle(): String? =
    when {
      ExperimentalUI.isNewUI() -> null
      !isFirstStep -> null
      repositories.size > 1 -> DvcsBundle.message("branch.popup.vcs.name.branches", GitDisplayName.NAME)
      else -> repositories.single().let {
        DvcsBundle.message("branch.popup.vcs.name.branches.in.repo", GitDisplayName.NAME, it.shortName)
      }
    }

  override fun shouldValidateNotNullTreeModel(): Boolean = !isFirstStep || super.shouldValidateNotNullTreeModel()

  fun isBranchesDiverged(): Boolean {
    return repositories.size > 1
           && GitRefUtil.getCommonCurrentBranch(repositories) == null
           && GitVcsSettings.getInstance(project).shouldExecuteOperationsOnAllRoots()
  }

  companion object {
    fun create(
      project: Project,
      selectedRepository: GitRepositoryFrontendModel?,
      repositories: List<GitRepositoryFrontendModel>,
    ): GitBranchesTreePopupStep = GitBranchesTreePopupStep(project, selectedRepository, repositories, true)

    /**
     * 2nd-level popup shown on repository click
     */
    private fun createPopupStepForSelectedRepo(project: Project, repository: GitRepositoryFrontendModel): GitBranchesTreePopupStep =
      GitBranchesTreePopupStep(project, repository, listOf(repository), false)

    private fun createActionStep(actionGroup: ActionGroup,
                                 project: Project,
                                 selectedRepository: GitRepositoryFrontendModel?,
                                 repositories: List<GitRepositoryFrontendModel>,
                                 reference: GitReference? = null): ListPopupStep<*> {
      val dataContext = createDataContext(project, selectedRepository, repositories, reference)
      return JBPopupFactory.getInstance()
        .createActionsStep(actionGroup, dataContext, GitBranchesWidgetActions.NESTED_POPUP_ACTION_PLACE, false, true, null, null, false, 0, false)
    }

    private fun List<PopupFactoryImpl.ActionItem>.addSeparators(): List<Any> {
      val actionsWithSeparators = mutableListOf<Any>()
      for (action in this) {
        if (action.isPrependWithSeparator) {
          actionsWithSeparators.add(GitBranchesTreePopupBase.createTreeSeparator(action.separatorText))
        }
        actionsWithSeparators.add(action)
      }
      return actionsWithSeparators
    }

    internal fun createDataContext(
      project: Project,
      selectedRepository: GitRepositoryFrontendModel?,
      repositories: List<GitRepositoryFrontendModel>,
      reference: GitReference? = null,
      component: JComponent? = null,
    ): DataContext =
      CustomizedDataContext.withSnapshot(DataManager.getInstance().getDataContext(component)) { sink ->
        sink[CommonDataKeys.PROJECT] = project
        sink[GitSingleRefActions.SELECTED_REF_DATA_KEY] = reference
        sink[GitBranchesWidgetKeys.SELECTED_REPOSITORY] = selectedRepository
        sink[GitBranchesWidgetKeys.AFFECTED_REPOSITORIES] = repositories
      }
  }
}