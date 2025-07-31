// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui.icons

import com.intellij.icons.AllIcons
import com.intellij.ide.IconProvider
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import javax.swing.Icon

class GHIconProvider : IconProvider() {
  override fun getIcon(element: PsiElement, flags: Int): Icon? {
    if (element is PsiDirectory && element.name == ".github") {
      return AllIcons.Nodes.FolderGithub
    }
    return null
  }
}