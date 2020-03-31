// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil

class IgnoredFileWritingAccessExtension(private val project: Project) : NonProjectFileWritingAccessExtension {
  override fun isWritable(file: VirtualFile) =
    IgnoredFileContentProvider.IGNORE_FILE_CONTENT_PROVIDER
      .getExtensions(project)
      .map(IgnoredFileContentProvider::getFileName).containsIgnoreCase(file.name) && VcsUtil.getVcsRootFor(project, file) != null
    || (FileTypeRegistry.getInstance().getFileTypeByFileName(file.nameSequence) is IgnoreFileType)

  private fun List<String>.containsIgnoreCase(element: String) = any { it.equals(element, true) }
}