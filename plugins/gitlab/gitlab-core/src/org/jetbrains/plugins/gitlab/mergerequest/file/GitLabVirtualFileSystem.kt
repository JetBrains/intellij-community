// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.databind.introspect.VisibilityChecker
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates

internal class GitLabVirtualFileSystem : ComplexPathVirtualFileSystem<GitLabVirtualFileSystem.FilePath>(PathSerializer()) {
  override fun getProtocol() = PROTOCOL

  override fun findOrCreateFile(project: Project, path: FilePath): VirtualFile? {
    if (project.locationHash != path.projectHash) return null

    return if (path.isDiff == true) {
      createDiffFile(path, project)
    }
    else {
      GitLabMergeRequestTimelineFile(path.sessionId, project, path.repository, path.mrIid)
    }
  }

  private fun createDiffFile(path: FilePath, project: Project): VirtualFile {
    return GitLabMergeRequestDiffFile(path.sessionId, project, path.repository, path.mrIid)
  }

  fun getPath(
    sessionId: String,
    project: Project,
    repository: GitLabProjectCoordinates,
    mrIid: String,
    isDiff: Boolean = false,
  ): String =
    getPath(FilePath(sessionId, project.locationHash, repository, mrIid, isDiff))

  internal data class FilePath(
    override val sessionId: String,
    override val projectHash: String,
    val repository: GitLabProjectCoordinates,
    val mrIid: String,
    val isDiff: Boolean? = null,
  ) : ComplexPath

  companion object {
    private const val PROTOCOL = "gitlabmr"

    fun getInstance() = service<VirtualFileManager>().getFileSystem(PROTOCOL) as GitLabVirtualFileSystem
  }

  private class PathSerializer : ComplexPathSerializer<FilePath> {
    private val mapper = jacksonObjectMapper().apply {
      setVisibility(VisibilityChecker.Std(JsonAutoDetect.Visibility.NONE,
                                          JsonAutoDetect.Visibility.NONE,
                                          JsonAutoDetect.Visibility.NONE,
                                          JsonAutoDetect.Visibility.NONE,
                                          JsonAutoDetect.Visibility.ANY))
    }

    override fun serialize(path: FilePath): String = mapper.writeValueAsString(path)
    override fun deserialize(rawPath: String): FilePath = mapper.readValue(rawPath, FilePath::class.java)
  }
}
