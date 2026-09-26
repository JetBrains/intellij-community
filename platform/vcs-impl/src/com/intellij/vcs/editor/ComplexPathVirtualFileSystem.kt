// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.editor

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePathWrapper

abstract class ComplexPathVirtualFileSystem<P : ComplexPathVirtualFileSystem.ComplexPath>(
  private val pathSerializer: ComplexPathSerializer<P>
) : DeprecatedVirtualFileSystem(), NonPhysicalFileSystem {
  protected abstract fun findOrCreateFile(project: Project, path: P): VirtualFile?

  fun getPath(path: P): String = pathSerializer.serialize(path)

  private fun getComplexPath(path: String): P = pathSerializer.deserialize(path)

  private fun getComplexPathSafe(path: String): P? {
    return try {
      getComplexPath(path)
    }
    catch (e: Exception) {
      LOG.warn("Cannot deserialize $path", e)
      return null
    }
  }

  override fun findFileByPath(path: String): VirtualFile? {
    val parsedPath = getComplexPathSafe(path) ?: return null
    val project = ProjectManagerEx.getInstanceEx().findOpenProjectByHash(parsedPath.projectHash) ?: return null
    return findOrCreateFile(project, parsedPath)
  }

  override fun refreshAndFindFileByPath(path: String) = findFileByPath(path)

  override fun extractPresentableUrl(path: String) = (refreshAndFindFileByPath(path) as? VirtualFilePathWrapper)?.presentablePath ?: path

  override fun refresh(asynchronous: Boolean) {}

  interface ComplexPath {
    /**
     * [sessionId] is required to differentiate files between launches.
     * This is necessary to make the files appear in "Recent Files" correctly.
     * Without this field files are saved in [com.intellij.openapi.fileEditor.impl.EditorHistoryManager] via pointers and urls are saved to disk
     * After reopening the project manager will try to restore the files and will not find them since necessary components are not ready yet
     * and despite this history entry will still be created using a url-only [com.intellij.openapi.vfs.impl.IdentityVirtualFilePointer] via
     * [com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl.getOrCreateIdentity] where pointers are cached.
     * As a result all previously opened files will be seen by history manager as non-existent.
     * Including this [sessionId] helps distinguish files between launches.
     */
    val sessionId: String
    val projectHash: String
  }

  interface ComplexPathSerializer<P : ComplexPath> {
    fun serialize(path: P): String
    fun deserialize(rawPath: String): P
  }

  companion object {
    private val LOG = logger<ComplexPathVirtualFileSystem<ComplexPath>>()
  }
}
