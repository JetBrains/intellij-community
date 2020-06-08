// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.introspect.VisibilityChecker
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.SimpleGHPRIdentifier

internal class GHPRVirtualFileSystem : DeprecatedVirtualFileSystem() {

  override fun getProtocol() = PROTOCOL

  override fun findFileByPath(path: String): VirtualFile? {
    val parsedPath = try {
      getPath(path)
    }
    catch (e: Exception) {
      LOG.debug("Cannot deserlialize file path", e)
      return null
    }

    val project = ProjectManagerEx.getInstanceEx().findOpenProjectByHash(parsedPath.projectHash) ?: return null
    val filesManager = GHPRDataContextRepository.getInstance(project).findContext(parsedPath.repository)?.filesManager ?: return null
    return if (parsedPath.isDiff) filesManager.findDiffFile(parsedPath.id) else filesManager.findTimelineFile(parsedPath.id)
  }

  override fun refreshAndFindFileByPath(path: String) = findFileByPath(path)

  override fun extractPresentableUrl(path: String) = (findFileByPath(path) as? GHPRVirtualFile)?.presentablePath ?: path

  override fun refresh(asynchronous: Boolean) {}

  companion object {
    private const val PROTOCOL = "ghpr"

    private val LOG = logger<GHPRVirtualFileSystem>()
    private val JACKSON = jacksonObjectMapper()
      .setSerializationInclusion(JsonInclude.Include.NON_NULL)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
      .setVisibility(VisibilityChecker.Std(JsonAutoDetect.Visibility.NONE,
                                           JsonAutoDetect.Visibility.NONE,
                                           JsonAutoDetect.Visibility.NONE,
                                           JsonAutoDetect.Visibility.NONE,
                                           JsonAutoDetect.Visibility.ANY))

    fun getPath(fileManagerId: String,
                project: Project,
                repository: GHRepositoryCoordinates,
                id: GHPRIdentifier,
                isDiff: Boolean = false): String =
      JACKSON.writeValueAsString(Path(fileManagerId, project.locationHash, repository, SimpleGHPRIdentifier(id), isDiff))

    private fun getPath(path: String) = JACKSON.readValue(path, Path::class.java)

    fun getInstance() = service<VirtualFileManager>().getFileSystem(PROTOCOL) as GHPRVirtualFileSystem
  }

  private data class Path(val fileManagerId: String,
                          val projectHash: String,
                          val repository: GHRepositoryCoordinates,
                          val id: SimpleGHPRIdentifier,
                          val isDiff: Boolean)
}
