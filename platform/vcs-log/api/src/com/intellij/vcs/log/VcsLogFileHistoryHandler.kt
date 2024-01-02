// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsFileRevisionEx
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface VcsLogFileHistoryHandler {

  val supportedVcs: VcsKey

  val isFastStartSupported: Boolean get() = true

  @Throws(VcsException::class)
  fun getHistoryFast(root: VirtualFile, filePath: FilePath, hash: Hash?, commitCount: Int): List<VcsFileRevisionEx>

  @Throws(VcsException::class)
  fun collectHistory(root: VirtualFile, filePath: FilePath, hash: Hash?, consumer: (VcsFileRevision) -> Unit) {
    throw UnsupportedOperationException("Not implemented")
  }

  @Throws(VcsException::class)
  fun getRename(root: VirtualFile, filePath: FilePath, beforeHash: Hash, afterHash: Hash): Rename?

  data class Rename(val filePath1: FilePath, val filePath2: FilePath, val hash1: Hash, val hash2: Hash)

  companion object {
    @JvmField
    val EP_NAME: ProjectExtensionPointName<VcsLogFileHistoryHandler> = ProjectExtensionPointName("com.intellij.vcsLogFileHistoryHandler")

    @JvmStatic
    fun getByVcs(project: Project, vcsKey: VcsKey) = EP_NAME.getExtensions(project).firstOrNull {it.supportedVcs == vcsKey }
  }
}