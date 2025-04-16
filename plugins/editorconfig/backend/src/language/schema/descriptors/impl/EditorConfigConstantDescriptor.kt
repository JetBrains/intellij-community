// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors.impl

import com.intellij.editorconfig.common.syntax.psi.EditorConfigFlatOptionKey
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValueIdentifier
import com.intellij.editorconfig.common.syntax.psi.EditorConfigQualifiedKeyPart
import com.intellij.psi.PsiElement
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptorVisitor
import org.editorconfig.language.schema.descriptors.EditorConfigMutableDescriptor
import org.editorconfig.language.util.EditorConfigTextMatchingUtil

data class EditorConfigConstantDescriptor(
  val text: String,
  override val documentation: String?,
  override val deprecation: String?
) : EditorConfigMutableDescriptor {
  override var parent: EditorConfigDescriptor? = null
  override fun toString(): String = text
  override fun accept(visitor: EditorConfigDescriptorVisitor): Unit = visitor.visitConstant(this)
  override fun matches(element: PsiElement): Boolean =
    (element is EditorConfigOptionValueIdentifier || element is EditorConfigQualifiedKeyPart || element is EditorConfigFlatOptionKey)
    && EditorConfigTextMatchingUtil.textMatchesToIgnoreCase(element, text)

  override fun getPresentableText(): String = text
}
