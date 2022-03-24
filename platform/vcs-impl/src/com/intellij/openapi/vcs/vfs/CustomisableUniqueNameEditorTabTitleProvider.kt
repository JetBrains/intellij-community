// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.vfs

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.fileEditor.impl.UniqueNameEditorTabTitleProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls

abstract class CustomisableUniqueNameEditorTabTitleProvider : UniqueNameEditorTabTitleProvider(), DumbAware {
  abstract fun isApplicable(file: VirtualFile): Boolean

  @NlsContexts.TabTitle
  abstract fun getEditorTabTitle(file: VirtualFile, @Nls baseUniqueName: String): String

  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    if (isApplicable(file)) {
      return getBaseUniqueName(project, file)?.let { baseName -> getEditorTabTitle(file, baseName) }
    }
    return null
  }

  @Nls
  private fun getBaseUniqueName(project: Project, file: VirtualFile): String? {
    var baseName = super.getEditorTabTitle(project, file)
    if (baseName == null && UISettings.getInstance().hideKnownExtensionInTabs && !file.isDirectory) {
      baseName = file.nameWithoutExtension.ifEmpty { file.name }
    }
    if (baseName == file.presentableName) return null
    return baseName
  }
}
