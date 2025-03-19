// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.create

import com.intellij.collaboration.async.extensionListFlow
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.bindValueIn
import com.intellij.collaboration.ui.codereview.commits.CommitsBrowserComponentBuilder
import com.intellij.collaboration.ui.codereview.create.CodeReviewCreateReviewLayoutBuilder
import com.intellij.collaboration.ui.codereview.create.CodeReviewCreateReviewUIUtil
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.Wrapper
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.ui.branch.MergeDirectionComponentFactory
import git4idea.ui.branch.MergeDirectionModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.model.*
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

internal object GitLabMergeRequestCreateComponentFactory {
  fun create(project: Project, cs: CoroutineScope, createVm: GitLabMergeRequestCreateViewModel): JComponent {
    val directionModel = GitLabMergeRequestCreateDirectionModel(createVm.projectsManager, createVm.projectData.projectMapping)
    cs.launchNow {
      createVm.branchState.filterNotNull().collect { branchState ->
        directionModel.baseBranch = branchState.baseBranch
        directionModel.setHead(branchState.baseRepo, branchState.headBranch)
      }
    }

    val branchState = BranchState.fromDirectionModel(directionModel)
    createVm.updateBranchState(branchState)
    directionModel.addDirectionChangesListener {
      val branchState = BranchState.fromDirectionModel(directionModel)
      createVm.updateBranchState(branchState)
      GitLabStatistics.logMrCreationBranchesChanged(project)
    }

    val directionSelector = createDirectionSelector(directionModel)
    val commitsLoadingPanel = createCommitsPanel(project, cs, createVm)
    val titlePanel = cs.createTitleEditorPanel(createVm)
    val reviewersPanel = GitLabMergeRequestCreateReviewersComponentFactory.create(cs, createVm)
    val statusPanel = GitLabMergeRequestCreateStatusComponentFactory.create(cs, createVm)
    val actionsPanel = GitLabMergeRequestCreateActionsComponentFactory.create(project, cs, createVm)

    return CodeReviewCreateReviewLayoutBuilder()
      .addComponent(directionSelector, zeroMinWidth = true)
      .addComponent(commitsLoadingPanel, stretchYWithWeight = 0.5f, withoutBorder = true)
      .addSeparator()
      .addComponent(titlePanel, stretchYWithWeight = 0.3f, zeroMinWidth = true)
      .addSeparator()
      .addComponent(reviewersPanel, zeroMinWidth = true, stretchYWithWeight = 0.2f)
      .addSeparator()
      .addComponent(statusPanel)
      .addComponent(actionsPanel, withListBackground = false)
      .build()
  }

  private fun CoroutineScope.createTitleEditorPanel(createVm: GitLabMergeRequestCreateViewModel): JComponent {
    val cs = this
    val editor = CodeReviewCreateReviewUIUtil.createTitleEditor(GitLabBundle.message("merge.request.create.title.placeholder")).apply {
      document.addDocumentListener(object : DocumentListener {
        fun updateText() = createVm.updateTitle(text)
        override fun insertUpdate(e: DocumentEvent?) = updateText()
        override fun removeUpdate(e: DocumentEvent?) = updateText()
        override fun changedUpdate(e: DocumentEvent?) = updateText()
      })
      bindTextIn(cs, createVm.title)
    }

    return Wrapper().apply {
      bindContentIn(cs, GitLabTitleGeneratorExtension.EP_NAME.extensionListFlow()) { extensions ->
        if (extensions.isEmpty()) {
          editor
        }
        else {
          wrapTitleEditorWithGenerateActions(createVm, editor)
        }
      }
    }
  }

  private fun CoroutineScope.wrapTitleEditorWithGenerateActions(
    createVm: GitLabMergeRequestCreateViewModel,
    editor: JComponent,
  ): JComponent {
    val wrappedEditor = UiDataProvider.wrapComponent(editor) {
      it[GitLabMergeRequestCreateTitleGenerationViewModel.DATA_KEY] = createVm.titleGenerationVm.value
    }

    val actionManager = ActionManager.getInstance()
    val actionGroup = actionManager.getAction("GitLab.Merge.Request.Create.Title.Actions") as ActionGroup
    val toolbar = actionManager.createActionToolbar("MrCreationTitle", actionGroup, true).apply {
      targetComponent = wrappedEditor
    }

    // Force an action's update with new values for commits and generating state
    launch {
      createVm.titleGenerationVm.collect {
        if (it == null) {
          toolbar.updateActionsAsync()
          return@collect
        }

        it.isGenerating.collect {
          toolbar.updateActionsAsync()
        }
      }
    }

    return CodeReviewCreateReviewUIUtil.createGenerationToolbarOverlay(wrappedEditor, toolbar)
  }

  private fun createDirectionSelector(directionModel: MergeDirectionModel<GitLabProjectMapping>): JComponent {
    return MergeDirectionComponentFactory(
      directionModel,
      getBaseRepoPresentation = { model ->
        val branch = model.baseBranch ?: return@MergeDirectionComponentFactory null
        val headRepoPath = model.headRepo?.repository?.projectPath
        val baseRepoPath = model.baseRepo.repository.projectPath

        val withOwner = headRepoPath != null && baseRepoPath != headRepoPath
        "${baseRepoPath.fullPath(withOwner)}:${branch.name}"
      },
      getHeadRepoPresentation = { model ->
        val branch = model.headBranch ?: return@MergeDirectionComponentFactory null
        val headRepoPath = model.headRepo?.repository?.projectPath ?: return@MergeDirectionComponentFactory null
        val baseRepoPath = model.baseRepo.repository.projectPath

        val withOwner = baseRepoPath != headRepoPath
        "${headRepoPath.fullPath(withOwner)}:${branch.name}"
      }
    ).create()
  }

  private fun createCommitsPanel(project: Project, cs: CoroutineScope, createVm: GitLabMergeRequestCreateViewModel): JComponent {
    val commitsModel = SingleValueModel(emptyList<VcsCommitMetadata>()).apply {
      bindValueIn(cs, createVm.commits.filterNotNull().map { result -> result.getOrDefault(emptyList()) })
    }
    val commitsPanel = CommitsBrowserComponentBuilder(project, commitsModel)
      .setCustomCommitRenderer(CodeReviewCreateReviewUIUtil.createCommitListCellRenderer())
      .showCommitDetails(false)
      .setEmptyCommitListText(GitLabBundle.message("merge.request.create.commits.empty.text"))
      .create()

    return CollaborationToolsUIUtil.wrapWithProgressStripe(cs, createVm.commits.map { it == null }, commitsPanel)
  }
}