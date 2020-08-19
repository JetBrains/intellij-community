// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import com.intellij.vcs.editor.GsonComplexPathSerializer
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.SimpleGHPRIdentifier

internal class GHPRVirtualFileSystem : ComplexPathVirtualFileSystem<GHPRVirtualFileSystem.GHPRFilePath>(
  GsonComplexPathSerializer(GHPRFilePath::class.java)
) {
  override fun getProtocol() = PROTOCOL

  override fun findFile(project: Project, path: GHPRFilePath): VirtualFile? {
    val filesManager = GHPRDataContextRepository.getInstance(project).findContext(path.repository)?.filesManager ?: return null
    return if (path.isDiff) filesManager.findDiffFile(path.prId) else filesManager.findTimelineFile(path.prId)
  }

  fun getPath(fileManagerId: String,
              project: Project,
              repository: GHRepositoryCoordinates,
              id: GHPRIdentifier,
              isDiff: Boolean = false): String =
    getPath(GHPRFilePath(fileManagerId, project.locationHash, repository, SimpleGHPRIdentifier(id), isDiff))

  data class GHPRFilePath(override val sessionId: String,
                          override val projectHash: String,
                          val repository: GHRepositoryCoordinates,
                          val prId: SimpleGHPRIdentifier,
                          val isDiff: Boolean) : ComplexPath

  companion object {
    private const val PROTOCOL = "ghpr"

    fun getInstance() = service<VirtualFileManager>().getFileSystem(PROTOCOL) as GHPRVirtualFileSystem
  }
}
