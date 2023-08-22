// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.diff.tools.combined.CombinedDiffVirtualFile
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates

internal abstract class GHPRCombinedDiffPreviewVirtualFileBase(sourceId: String,
                                                               protected val fileManagerId: String,
                                                               protected val project: Project,
                                                               protected val repository: GHRepositoryCoordinates):
  CombinedDiffVirtualFile(sourceId, ""), VirtualFilePathWrapper {

  override fun getFileSystem(): VirtualFileSystem = GHPRVirtualFileSystem.getInstance()
  override fun getFileType(): FileType = FileTypes.UNKNOWN

  override fun getLength() = 0L
  override fun contentsToByteArray() = throw UnsupportedOperationException()
  override fun getInputStream() = throw UnsupportedOperationException()
  override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = throw UnsupportedOperationException()

  override fun enforcePresentableName(): Boolean = true

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GHPRCombinedDiffPreviewVirtualFileBase

    if (fileManagerId != other.fileManagerId) return false
    if (project != other.project) return false
    if (repository != other.repository) return false

    return true
  }

  override fun hashCode(): Int {
    var result = fileManagerId.hashCode()
    result = 31 * result + project.hashCode()
    result = 31 * result + repository.hashCode()
    return result
  }
}
