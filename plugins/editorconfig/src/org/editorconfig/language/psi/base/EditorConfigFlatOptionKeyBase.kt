// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.psi.base

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.editorconfig.language.psi.EditorConfigFlatOptionKey
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.psi.reference.EditorConfigFlatOptionKeyReference

abstract class EditorConfigFlatOptionKeyBase(node: ASTNode) : EditorConfigIdentifierElementBase(node), EditorConfigFlatOptionKey {
  final override fun setName(name: String): PsiElement = throw UnsupportedOperationException()
  final override fun getReference(): EditorConfigFlatOptionKeyReference = EditorConfigFlatOptionKeyReference(this)
  final override fun getDescriptor(smart: Boolean) = option.getDescriptor(smart)?.key
  final override fun getPresentation() = PresentationData(text, declarationSite, IconManager.getInstance().getPlatformIcon(
    PlatformIcons.Property), null)

  final override fun definesSameOption(element: EditorConfigFlatOptionKey): Boolean {
    if (element !is EditorConfigDescribableElement) return false
    val descriptor = option.getDescriptor(false)
    val otherDescriptor = element.option.getDescriptor(false)

    if (otherDescriptor == null && descriptor == null) return textMatches(element)
    return descriptor == otherDescriptor
  }
}
