// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.util.selectedChange
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.tools.combined.COMBINED_DIFF_VIEWER_KEY
import com.intellij.diff.tools.combined.CombinedDiffModelImpl
import com.intellij.diff.tools.combined.CombinedPathBlockId
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewModel
import com.intellij.util.cancelOnDispose
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModel

internal fun createGitLabCombinedDiffModel(connectionId: String,
                                          project: Project,
                                          mergeRequestIid: String): CombinedDiffModelImpl {
  val job = SupervisorJob()
  val model = CombinedDiffModelImpl(project, null).apply {
    context.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS,
                        listOf(ActionManager.getInstance().getAction("GitLab.MergeRequest.Review.Submit")))
  }
  val uiCs = CoroutineScope(job + Dispatchers.Main.immediate + CoroutineName("GitLab Merge Request Review Combined Diff UI"))
  job.cancelOnDispose(model.ourDisposable)

  val projectVm = findProjectVm(project, connectionId)
  if (projectVm != null) {
    uiCs.launchNow {
      projectVm.getDiffViewModel(mergeRequestIid).collectLatest {

        val diffVm = it.getOrNull() ?: return@collectLatest
        model.context.putUserData(GitLabMergeRequestDiffViewModel.KEY, diffVm)

        diffVm.changes.collectLatest { changes ->
          var myChanges: List<Change> = emptyList()
          val branchComparisonResult = loadRevisionsAndParseChanges(changes)

          diffVm.changesToShow.collectLatest { changeSelection ->
            val newChanges = changeSelection.changes

            // fixme: fix after selection rework
            val onlySelectionChangedInTree = newChanges === myChanges

            if (!onlySelectionChangedInTree || model.requests.isEmpty()) {

              myChanges = newChanges
              val list: List<ChangeViewDiffRequestProcessor.ChangeWrapper> = myChanges.map { change ->
                GitLabMergeRequestChangeWrapper(change, branchComparisonResult)
              }

              model.cleanBlocks()
              model.setBlocks(CombinedDiffPreviewModel.prepareCombinedDiffModelRequests(project, list))
            }

            val change = changeSelection.selectedChange ?: return@collectLatest
            val diffViewer = model.context.getUserData(COMBINED_DIFF_VIEWER_KEY) ?: return@collectLatest
            diffViewer.selectDiffBlock(CombinedPathBlockId(ChangesUtil.getFilePath(change), change.fileStatus), focusBlock = false)
          }
        }

        try {
          awaitCancellation()
        }
        catch (e: Exception) {
          model.cleanBlocks()
          model.context.putUserData(GitLabMergeRequestDiffViewModel.KEY, null)
        }
      }
    }
  }

  return model
}

private class GitLabMergeRequestChangeWrapper(
  change: Change,
  private val result: GitBranchComparisonResult
) : ChangeViewDiffRequestProcessor.ChangeWrapper(change) {

  override fun createProducer(project: Project?): DiffRequestProducer? {
    val data = createData(result, change, null)
    return ChangeDiffRequestProducer.create(project, change, data)
  }
}