// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

/**
 * [fileManagerId] is a [org.jetbrains.plugins.github.pullrequest.data.GHPRFilesManagerImpl.id] which is required to differentiate files
 * between launches of a PR toolwindow.
 * This is necessary to make the files appear in "Recent Files" correctly.
 * See [com.intellij.vcs.editor.ComplexPathVirtualFileSystem.ComplexPath.sessionId] for details.
 */
abstract class GHPRVirtualFile(fileManagerId: String,
                               project: Project,
                               repository: GHRepositoryCoordinates,
                               val pullRequest: GHPRIdentifier)
  : GHRepoVirtualFile(fileManagerId, project, repository) {

  override fun enforcePresentableName() = true

  override fun getFileSystem(): VirtualFileSystem = GHPRVirtualFileSystem.getInstance()
  override fun getFileType(): FileType = FileTypes.UNKNOWN

  override fun getLength() = 0L
  override fun contentsToByteArray() = throw UnsupportedOperationException()
  override fun getInputStream() = throw UnsupportedOperationException()
  override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = throw UnsupportedOperationException()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRVirtualFile) return false
    if (!super.equals(other)) return false

    if (pullRequest != other.pullRequest) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + pullRequest.hashCode()
    return result
  }
}