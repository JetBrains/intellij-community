// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import com.intellij.vcs.editor.GsonComplexPathSerializer
import com.intellij.vcs.log.VcsLogFilterCollection

internal class VcsLogVirtualFileSystem :
  ComplexPathVirtualFileSystem<VcsLogVirtualFileSystem.VcsLogComplexPath>(GsonComplexPathSerializer(VcsLogComplexPath::class.java)) {

  override fun findOrCreateFile(project: Project, path: VcsLogComplexPath): VirtualFile {
    return createVcsLogFile(path, null)
  }

  fun createVcsLogFile(project: Project, tabId: String, filters: VcsLogFilterCollection?): VirtualFile {
    return createVcsLogFile(VcsLogComplexPath(project.locationHash, project.locationHash, tabId), filters)
  }

  private fun createVcsLogFile(pathId: VcsLogComplexPath, filters: VcsLogFilterCollection?): DefaultVcsLogFile {
    return DefaultVcsLogFile(pathId, filters)
  }

  override fun getProtocol(): String = Holder.PROTOCOL

  object Holder {
    internal const val PROTOCOL = "vcs-log"

    @JvmStatic
    fun getInstance() = service<VirtualFileManager>().getFileSystem(PROTOCOL) as VcsLogVirtualFileSystem
  }

  data class VcsLogComplexPath(override val sessionId: String,
                               override val projectHash: String,
                               val logId: String) : ComplexPath
}
