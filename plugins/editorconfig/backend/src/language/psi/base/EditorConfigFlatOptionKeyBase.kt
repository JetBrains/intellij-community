// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.psi.base

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.editorconfig.language.psi.EditorConfigFlatOptionKey

abstract class EditorConfigFlatOptionKeyBase(node: ASTNode) : EditorConfigIdentifierElementBase(node), EditorConfigFlatOptionKey {
  final override fun setName(name: String): PsiElement = throw UnsupportedOperationException()
  final override fun getPresentation(): PresentationData = PresentationData(text, declarationSite, IconManager.getInstance().getPlatformIcon(
    PlatformIcons.Property), null)
}
