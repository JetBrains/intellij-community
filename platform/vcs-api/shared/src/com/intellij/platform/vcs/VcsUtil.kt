// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.IconUtil.getIcon
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
object VcsUtil {
  const val SHORT_HASH_LENGTH: Int = 8

  /**
   * Interaction with [VirtualFile] is not supported in the Split mode
   */
  @JvmStatic
  @ApiStatus.Obsolete
  fun getFilePath(file: VirtualFile): FilePath = VcsContextFactory.getInstance().createFilePathOn(file)

  @JvmStatic
  fun getIcon(project: Project?, filePath: FilePath): Icon? {
    if (project == null || project.isDisposed()) return null

    val virtualFile = filePath.virtualFile

    return if (virtualFile != null) getIcon(virtualFile, 0, project)
    else FileTypeManager.getInstance().getFileTypeByFileName(filePath.getName()).getIcon()
  }

  @JvmStatic
  fun getShortHash(hashString: String): @NlsSafe String = hashString.take(SHORT_HASH_LENGTH)

  @JvmStatic
  fun getShortHash(hashString: String, shortHashLength: Int): @NlsSafe String = hashString.take(shortHashLength)
}