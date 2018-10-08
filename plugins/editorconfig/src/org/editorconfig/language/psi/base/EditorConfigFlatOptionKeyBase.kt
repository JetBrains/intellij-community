// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.base

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.editorconfig.language.psi.EditorConfigFlatOptionKey
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.psi.reference.EditorConfigFlatOptionKeyReference

abstract class EditorConfigFlatOptionKeyBase(node: ASTNode) : EditorConfigIdentifierElementBase(node), EditorConfigFlatOptionKey {
  final override fun setName(name: String): PsiElement = throw UnsupportedOperationException()
  final override fun getReference(): EditorConfigFlatOptionKeyReference = EditorConfigFlatOptionKeyReference(this)
  final override fun getDescriptor(smart: Boolean) = option.getDescriptor(smart)?.key
  final override fun getPresentation() = PresentationData(text, declarationSite, AllIcons.Nodes.Property, null)

  final override fun definesSameOption(element: EditorConfigFlatOptionKey): Boolean {
    element as? EditorConfigDescribableElement ?: return false
    val descriptor = option.getDescriptor(false)
    val otherDescriptor = element.option.getDescriptor(false)

    if (otherDescriptor == null && descriptor == null) return textMatches(element)
    return descriptor == otherDescriptor
  }
}
