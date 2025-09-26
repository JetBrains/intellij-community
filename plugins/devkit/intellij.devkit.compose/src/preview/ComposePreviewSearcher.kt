// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ext.LibrarySearchHelper
import org.jetbrains.idea.devkit.util.PsiUtil

internal class ComposePreviewSearcher : LibrarySearchHelper {
  override fun isLibraryExists(project: Project): Boolean {
    // only in plugin projects to not conflict with other previews
    return PsiUtil.isPluginProject(project)
  }
}
