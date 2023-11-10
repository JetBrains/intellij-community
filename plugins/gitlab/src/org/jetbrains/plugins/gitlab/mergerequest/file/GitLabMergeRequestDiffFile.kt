// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.file.codereview.CodeReviewCombinedDiffVirtualFile
import com.intellij.collaboration.file.codereview.CodeReviewDiffVirtualFile
import com.intellij.collaboration.ui.codereview.diff.CodeReviewDiffHandlerHelper
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.combined.CombinedDiffModelImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal data class GitLabMergeRequestDiffFile(
  override val connectionId: String,
  private val project: Project,
  private val glProject: GitLabProjectCoordinates,
  private val mergeRequestIid: String
) : CodeReviewDiffVirtualFile(getFileName(mergeRequestIid)),
    VirtualFilePathWrapper,
    GitLabVirtualFile {

  override fun getFileSystem(): ComplexPathVirtualFileSystem<*> = GitLabVirtualFileSystem.getInstance()

  override fun getPath(): String = (fileSystem as GitLabVirtualFileSystem).getPath(connectionId, project, glProject, mergeRequestIid, true)
  override fun getPresentablePath(): String = getPresentablePath(glProject, mergeRequestIid)
  override fun getPresentableName(): String = GitLabBundle.message("merge.request.diff.file.name", mergeRequestIid)

  override fun isValid(): Boolean = isFileValid(project, connectionId)

  override fun createProcessor(project: Project): DiffRequestProcessor =
    project.service<GitLabMergeRequestDiffService>().createDiffRequestProcessor(connectionId, mergeRequestIid)
}

internal data class GitLabMergeRequestCombinedDiffFile(
  override val connectionId: String,
  private val project: Project,
  private val glProject: GitLabProjectCoordinates,
  private val mergeRequestIid: String
) : CodeReviewCombinedDiffVirtualFile(createSourceId(connectionId, glProject, mergeRequestIid), getFileName(mergeRequestIid)),
    VirtualFilePathWrapper,
    GitLabVirtualFile {

  override fun getFileSystem(): ComplexPathVirtualFileSystem<*> = GitLabVirtualFileSystem.getInstance()

  override fun getPath(): String = (fileSystem as GitLabVirtualFileSystem).getPath(connectionId, project, glProject, mergeRequestIid, true)
  override fun getPresentablePath(): String = getPresentablePath(glProject, mergeRequestIid)
  override fun getPresentableName(): String = GitLabBundle.message("merge.request.diff.file.name", mergeRequestIid)

  override fun isValid(): Boolean = isFileValid(project, connectionId)

  override fun createModel(id: String): CombinedDiffModelImpl =
    project.service<GitLabMergeRequestDiffService>().createGitLabCombinedDiffModel(connectionId, mergeRequestIid)
}

private fun createSourceId(connectionId: String, glProject: GitLabProjectCoordinates, mergeRequestIid: String) =
  "GitLabMergeRequest:$connectionId:$glProject/$mergeRequestIid"

private fun getFileName(mergeRequestIid: String): String = "$mergeRequestIid.diff"

private fun getPresentablePath(glProject: GitLabProjectCoordinates, mergeRequestIid: String): String =
  "$glProject/mergerequests/${mergeRequestIid}.diff"

private fun isFileValid(project: Project, connectionId: String): Boolean =
  project.serviceIfCreated<GitLabToolWindowViewModel>()?.projectVm?.value.takeIf { it?.connectionId == connectionId } != null

@Service(Service.Level.PROJECT)
private class GitLabMergeRequestDiffService(private val project: Project, parentCs: CoroutineScope) {
  private val base = CodeReviewDiffHandlerHelper(project, parentCs)

  fun createDiffRequestProcessor(connectionId: String, mergeRequestIid: String): DiffRequestProcessor {
    val vm = findDiffVm(project, connectionId, mergeRequestIid)
    return base.createDiffRequestProcessor(vm, GitLabMergeRequestDiffViewModel.KEY)
  }

  fun createGitLabCombinedDiffModel(connectionId: String, mergeRequestIid: String): CombinedDiffModelImpl {
    val vm = findDiffVm(project, connectionId, mergeRequestIid)
    return base.createCombinedDiffModel(vm, GitLabMergeRequestDiffViewModel.KEY)
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
