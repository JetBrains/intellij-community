// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.platform.vcs.rd.ThinClientVcsContextFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
data class FilePathDto(
  private val virtualFileId: VirtualFileId?,
  private val path: String,
  private val isDirectory: Boolean,
  @Transient private val localFilePath: FilePath? = null,
) {
  val filePath: FilePath by lazy {
    if (localFilePath != null) return@lazy localFilePath
    val vcsContextFactory = VcsContextFactory.getInstance()
    if (vcsContextFactory is ThinClientVcsContextFactory)
      vcsContextFactory.createFilePathOn(path, isDirectory, virtualFileId)
    else
      vcsContextFactory.createFilePath(path, isDirectory)
  }

  override fun toString(): String = "$path (isDirectory=$isDirectory)"

  companion object {
    fun toDto(path: FilePath): FilePathDto = FilePathDto(
      virtualFileId = path.virtualFile?.rpcId(),
      path = path.path,
      isDirectory = path.isDirectory,
      localFilePath = path,
    )
  }
}