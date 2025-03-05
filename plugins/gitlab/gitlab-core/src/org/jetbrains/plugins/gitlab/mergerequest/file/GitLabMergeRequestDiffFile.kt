// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.file.codereview.CodeReviewDiffVirtualFile
import com.intellij.collaboration.ui.codereview.CodeReviewAdvancedSettings
import com.intellij.collaboration.ui.codereview.diff.AsyncDiffRequestProcessorFactory
import com.intellij.collaboration.util.KeyValuePair
import com.intellij.collaboration.util.filePath
import com.intellij.collaboration.util.fileStatus
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.combined.CombinedDiffComponentProcessor
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diff.impl.GenericDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffChangeViewModel
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal data class GitLabMergeRequestDiffFile(
  override val connectionId: String,
  private val project: Project,
  private val glProject: GitLabProjectCoordinates,
  private val mergeRequestIid: String,
) : CodeReviewDiffVirtualFile(getFileName(mergeRequestIid)),
    VirtualFilePathWrapper,
    GitLabVirtualFile {

  override fun getFileSystem(): ComplexPathVirtualFileSystem<*> = GitLabVirtualFileSystem.getInstance()

  override fun getPath(): String = (fileSystem as GitLabVirtualFileSystem).getPath(connectionId, project, glProject, mergeRequestIid, true)
  override fun getPresentablePath(): String = getPresentablePath(glProject, mergeRequestIid)
  override fun getPresentableName(): String = GitLabBundle.message("merge.request.diff.file.name", mergeRequestIid)

  override fun isValid(): Boolean = isFileValid(project, connectionId)

  override fun createViewer(project: Project): DiffEditorViewer {
    val processor = if (CodeReviewAdvancedSettings.isCombinedDiffEnabled()) {
      project.service<GitLabMergeRequestDiffService>().createGitLabCombinedDiffProcessor(connectionId, mergeRequestIid)
    }
    else {
      project.service<GitLabMergeRequestDiffService>().createDiffRequestProcessor(connectionId, mergeRequestIid)
    }
    processor.context.putUserData(DiffUserDataKeysEx.COMBINED_DIFF_TOGGLE, CodeReviewAdvancedSettings.CodeReviewCombinedDiffToggle)
    return processor
  }
}

private fun getFileName(mergeRequestIid: String): String = "$mergeRequestIid.diff"

private fun getPresentablePath(glProject: GitLabProjectCoordinates, mergeRequestIid: String): String =
  "$glProject/mergerequests/${mergeRequestIid}.diff"

private fun isFileValid(project: Project, connectionId: String): Boolean =
  project.serviceIfCreated<GitLabToolWindowViewModel>()?.projectVm?.value.takeIf { it?.connectionId == connectionId } != null

@Service(Service.Level.PROJECT)
private class GitLabMergeRequestDiffService(private val project: Project, private val cs: CoroutineScope) {
  fun createDiffRequestProcessor(connectionId: String, mergeRequestIid: String): DiffRequestProcessor {
    val vmFlow = findDiffVm(project, connectionId, mergeRequestIid)
    return AsyncDiffRequestProcessorFactory.createIn(cs, project, vmFlow, ::createDiffContext, ::getChangeDiffVmPresentation)
  }

  fun createGitLabCombinedDiffProcessor(connectionId: String, mergeRequestIid: String): CombinedDiffComponentProcessor {
    val vmFlow = findDiffVm(project, connectionId, mergeRequestIid)
    return AsyncDiffRequestProcessorFactory.createCombinedIn(cs, project, vmFlow, ::createDiffContext, ::getChangeDiffVmPresentation)
  }

  private fun createDiffContext(vm: GitLabMergeRequestDiffViewModel): List<KeyValuePair<*>> = buildList {
    add(KeyValuePair(GitLabMergeRequestDiffViewModel.KEY, vm))
    add(KeyValuePair(DiffUserDataKeys.DATA_PROVIDER, GenericDataProvider().apply {
      putData(GitLabMergeRequestReviewViewModel.DATA_KEY, vm)
    }))
    add(KeyValuePair(DiffUserDataKeys.CONTEXT_ACTIONS,
                     listOf(ActionManager.getInstance().getAction("GitLab.MergeRequest.Review.Submit"))))
  }

  private fun getChangeDiffVmPresentation(changeVm: GitLabMergeRequestDiffChangeViewModel): PresentableChange =
    object : PresentableChange {
      override fun getFilePath(): FilePath = changeVm.change.filePath
      override fun getFileStatus(): FileStatus = changeVm.change.fileStatus
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
