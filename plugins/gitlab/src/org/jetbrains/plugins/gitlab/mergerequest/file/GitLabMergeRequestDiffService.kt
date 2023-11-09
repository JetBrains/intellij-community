// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.ui.codereview.diff.CodeReviewDiffHandlerHelper
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.combined.CombinedDiffModelImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowViewModel

@Service(Service.Level.PROJECT)
internal class GitLabMergeRequestDiffService(private val project: Project, parentCs: CoroutineScope) {
  private val cs = parentCs.childScope(Dispatchers.Main.immediate)

  fun createDiffRequestProcessor(projectVm: GitLabToolWindowProjectViewModel, mergeRequestIid: String): DiffRequestProcessor {
    val processor = MutableDiffRequestChainProcessor(project, SimpleDiffRequestChain(LoadingDiffRequest()))
    cs.launchNow(CoroutineName("GitLab Merge Request Review Diff UI")) {
      projectVm.getDiffViewModel(mergeRequestIid).collectLatest {
        val diffVm = it.getOrNull() ?: return@collectLatest
        processor.putContextUserData(GitLabMergeRequestDiffViewModel.KEY, diffVm)

        launch {
          diffVm.submittableReview.collectLatest {
            processor.toolbar.updateActionsAsync()
          }
        }

        try {
          handleChanges(diffVm, processor)
          awaitCancellation()
        }
        catch (e: Exception) {
          processor.chain = null
          processor.putContextUserData(GitLabMergeRequestDiffViewModel.KEY, null)
        }
      }
    }.cancelOnDispose(processor)
    return processor
  }

  fun createDiffRequestProcessor(connectionId: String, mergeRequestIid: String): DiffRequestProcessor {
    val vm = findDiffVm(project, connectionId, mergeRequestIid)
    return base.createDiffRequestProcessor(vm, GitLabMergeRequestDiffViewModel.KEY)
  }

  fun createGitLabCombinedDiffModel(connectionId: String, mergeRequestIid: String): CombinedDiffModelImpl {
    val vm = findDiffVm(project, connectionId, mergeRequestIid)
    return base.createCombinedDiffModel(vm, GitLabMergeRequestDiffViewModel.KEY)
  }

  companion object {
    fun isDiffFileValid(project: Project, connectionId: String): Boolean =
      project.serviceIfCreated<GitLabToolWindowViewModel>()?.projectVm?.value.takeIf { it?.connectionId == connectionId } != null
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun findDiffVm(project: Project, connectionId: String, mergeRequestIid: String): Flow<GitLabMergeRequestDiffViewModel?> {
  val projectVm = project.serviceIfCreated<GitLabToolWindowViewModel>()?.projectVm ?: return flowOf(null)
  return projectVm.flatMapLatest {
    if (it != null && it.connectionId == connectionId) {
      it.getDiffViewModel(mergeRequestIid).map { it.getOrNull() }
    }
    else {
      flowOf(null)
    }
  }
}