// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.ui.codereview.diff.MutableDiffRequestChainProcessor
import com.intellij.diff.editor.DiffVirtualFile
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffModelRepository
import org.jetbrains.plugins.gitlab.util.GitLabBundle

class GitLabMergeRequestDiffFile(override val connectionId: String,
                                 private val project: Project,
                                 private val glProject: GitLabProjectCoordinates,
                                 val mergeRequestId: GitLabMergeRequestId)
  : DiffVirtualFile(GitLabBundle.message("merge.request.diff.file.name", mergeRequestId.iid)),
    VirtualFilePathWrapper,
    GitLabVirtualFile {

  init {
    putUserData(SplitAction.FORBID_TAB_SPLIT, true)
  }

  override fun enforcePresentableName() = true

  override fun isValid(): Boolean = findConnection() != null

  override fun getPath(): String =
    (fileSystem as GitLabVirtualFileSystem).getPath(connectionId, project, glProject, mergeRequestId, true)

  override fun getPresentablePath(): String = "$glProject/mergerequests/${mergeRequestId.iid}.diff"

  private fun findConnection() = project.serviceIfCreated<GitLabProjectConnectionManager>()?.connectionState?.value?.takeIf {
    it.id == connectionId
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun createProcessor(project: Project): DiffRequestProcessor {
    val connection = findConnection() ?: error("Missing connection for $this")
    val vmFlow = project.serviceIfCreated<GitLabMergeRequestDiffModelRepository>()?.getShared(connection, mergeRequestId)
                 ?: error("Missing diff model for $this")

    val job = SupervisorJob()
    val cs = CoroutineScope(job + Dispatchers.Main.immediate)

    val processor = object : MutableDiffRequestChainProcessor(project, null) {
      override fun selectFilePath(filePath: FilePath) {
        cs.launch(start = CoroutineStart.UNDISPATCHED) {
          vmFlow.first().selectFilePath(filePath)
        }
      }
    }
    job.cancelOnDispose(processor)

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      vmFlow.flatMapLatest {
        it.chain
      }.collectLatest {
        processor.chain = it
      }
    }
    return processor
  }

  override fun getFileSystem(): VirtualFileSystem = GitLabVirtualFileSystem.getInstance()
  override fun getFileType(): FileType = FileTypes.UNKNOWN

  override fun getLength() = 0L
  override fun contentsToByteArray() = throw UnsupportedOperationException()
  override fun getInputStream() = throw UnsupportedOperationException()
  override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = throw UnsupportedOperationException()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabMergeRequestDiffFile) return false

    if (connectionId != other.connectionId) return false
    if (project != other.project) return false
    if (glProject != other.glProject) return false
    return mergeRequestId == other.mergeRequestId
  }

  override fun hashCode(): Int {
    var result = connectionId.hashCode()
    result = 31 * result + project.hashCode()
    result = 31 * result + glProject.hashCode()
    result = 31 * result + mergeRequestId.hashCode()
    return result
  }

  override fun toString(): String =
    "GitLabMergeRequestDiffFile(connectionId='$connectionId', project=$project, glProject=$glProject, mergeRequestId=$mergeRequestId)"
}