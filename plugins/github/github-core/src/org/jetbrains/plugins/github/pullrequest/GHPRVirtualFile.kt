// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.file.ComplexPathVirtualFileWithoutContent
import com.intellij.openapi.project.Project
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

/**
 * @param fileManagerId [org.jetbrains.plugins.github.pullrequest.data.GHPRFilesManagerImpl.id]
 */
abstract class GHPRVirtualFile(fileManagerId: String,
                               val project: Project,
                               val repository: GHRepositoryCoordinates,
                               val pullRequest: GHPRIdentifier)
  : ComplexPathVirtualFileWithoutContent(fileManagerId) {

  override fun getFileSystem(): ComplexPathVirtualFileSystem<*> = GHPRVirtualFileSystem.getInstance()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRVirtualFile) return false
    if (!super.equals(other)) return false

    if (project != other.project) return false
    if (repository != other.repository) return false
    return pullRequest == other.pullRequest
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + project.hashCode()
    result = 31 * result + repository.hashCode()
    result = 31 * result + pullRequest.hashCode()
    return result
  }
}