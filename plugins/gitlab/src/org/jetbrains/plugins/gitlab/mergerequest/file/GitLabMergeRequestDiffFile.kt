// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.ui.codereview.diff.MutableDiffRequestChainProcessor
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.editor.DiffVirtualFile
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.requests.LoadingDiffRequest
import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.util.cancelOnDispose
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffBridge
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffBridgeRepository
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffReviewViewModelImpl
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

  override fun createProcessor(project: Project): DiffRequestProcessor {
    val connection = findConnection() ?: error("Missing connection for $this")
    val diffBridge: GitLabMergeRequestDiffBridge =
      project.serviceIfCreated<GitLabMergeRequestDiffBridgeRepository>()?.get(connection, mergeRequestId)
      ?: error("Missing diff model for $this")

    val job = SupervisorJob()
    val cs = CoroutineScope(job)

    val reviewVm = GitLabMergeRequestDiffReviewViewModelImpl(cs, connection.currentUser, connection.projectData, mergeRequestId)

    val uiCs = cs.childScope(Dispatchers.Main.immediate)
    val processor = MutableDiffRequestChainProcessor(project, SimpleDiffRequestChain(LoadingDiffRequest())).apply {
      putContextUserData(GitLabProjectConnection.KEY, connection)
      putContextUserData(GitLabMergeRequestDiffReviewViewModel.KEY, reviewVm)

      selectionEventDispatcher.addListener {
        diffBridge.selectFilePath(it.filePath)
      }
    }

    job.cancelOnDispose(processor)

    uiCs.launch(start = CoroutineStart.UNDISPATCHED) {
      diffBridge.chain.collectLatest {
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