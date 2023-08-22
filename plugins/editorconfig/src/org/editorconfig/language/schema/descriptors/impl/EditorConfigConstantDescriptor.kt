// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors.impl

import com.intellij.psi.PsiElement
import org.editorconfig.language.psi.EditorConfigFlatOptionKey
import org.editorconfig.language.psi.EditorConfigOptionValueIdentifier
import org.editorconfig.language.psi.EditorConfigQualifiedKeyPart
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
  override fun toString() = text
  override fun accept(visitor: EditorConfigDescriptorVisitor) = visitor.visitConstant(this)
  override fun matches(element: PsiElement) =
    (element is EditorConfigOptionValueIdentifier || element is EditorConfigQualifiedKeyPart || element is EditorConfigFlatOptionKey)
    && EditorConfigTextMatchingUtil.textMatchesToIgnoreCase(element, text)

  override fun getPresentableText(): String = text
}
