// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ModifiedWithoutEditingViewDialog
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls

internal class ModifiedWithoutEditingNode(
  private val project: Project,
  files: List<VirtualFile>,
) : ChangesBrowserSpecificFilesNode<ChangesBrowserNode.Tag>(MODIFIED_WITHOUT_EDITING_TAG, files,
                                                            files.count { it.isDirectory },
                                                            { if (!project.isDisposed) ModifiedWithoutEditingViewDialog(project).show() }) {

  @Nls
  override fun getTextPresentation(): String = getUserObject().toString() //NON-NLS
}
