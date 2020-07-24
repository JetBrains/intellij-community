// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.ide.FileIconProvider
import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.openapi.vfs.VirtualFileWithoutContent
import icons.GithubIcons
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import javax.swing.Icon

/**
 * [fileManagerId] is a [org.jetbrains.plugins.github.pullrequest.data.GHPRFilesManagerImpl.id] which is required to differentiate files
 * between launches of a PR toolwindow.
 * This is necessary to make the files appear in "Recent Files" correctly.
 * Without this field files are saved in [com.intellij.openapi.fileEditor.impl.EditorHistoryManager] via pointers and urls are saved to disk
 * After reopening the project manager will try to restore the files and will not find them since data context is not available at that time
 * and despite this history entry will still be created using a url-only [com.intellij.openapi.vfs.impl.IdentityVirtualFilePointer] via
 * [com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl.getOrCreateIdentity] where pointers are cached.
 * As a result all previously opened files will be seen by history manager as non-existent.
 * Including this arbitrary [fileManagerId] helps distinguish files between launches.
 */
internal class GHPRVirtualFile(private val fileManagerId: String,
                               val project: Project,
                               val repository: GHRepositoryCoordinates,
                               val pullRequest: GHPRIdentifier)
  : VirtualFile(), VirtualFileWithoutContent, VirtualFilePathWrapper {

  var valid: Boolean = true

  var details: GHPullRequestShort? = pullRequest as? GHPullRequestShort

  init {
    putUserData(SplitAction.FORBID_TAB_SPLIT, true)
  }

  override fun getName() = "#${pullRequest.number}"
  override fun getPresentableName(): String? = details?.title ?: name

  override fun getPath(): String = GHPRVirtualFileSystem.getPath(fileManagerId, project, repository, pullRequest)
  override fun getPresentablePath() = details?.url ?: "${repository.toUrl()}/pulls/${pullRequest.number}"

  private fun getIcon(): Icon? = when (details?.state) {
    GHPullRequestState.CLOSED -> GithubIcons.PullRequestClosed
    GHPullRequestState.MERGED -> GithubIcons.PullRequestMerged
    GHPullRequestState.OPEN -> GithubIcons.PullRequestOpen
    null -> null
  }

  override fun enforcePresentableName() = true

  override fun getFileSystem(): VirtualFileSystem = GHPRVirtualFileSystem.getInstance()

  override fun isValid() = valid

  override fun isDirectory() = false
  override fun getLength() = 0L
  override fun getTimeStamp() = 0L
  override fun isWritable() = false
  override fun getParent() = null
  override fun getChildren() = null
  override fun contentsToByteArray() = throw UnsupportedOperationException()
  override fun getInputStream() = throw UnsupportedOperationException()
  override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = throw UnsupportedOperationException()

  override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRVirtualFile) return false

    if (fileManagerId != other.fileManagerId) return false
    if (project != other.project) return false
    if (repository != other.repository) return false
    if (pullRequest != other.pullRequest) return false

    return true
  }

  override fun hashCode(): Int {
    var result = fileManagerId.hashCode()
    result = 31 * result + project.hashCode()
    result = 31 * result + repository.hashCode()
    result = 31 * result + pullRequest.hashCode()
    return result
  }


  class IconProvider : FileIconProvider {
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? = (file as? GHPRVirtualFile)?.getIcon()
  }
}