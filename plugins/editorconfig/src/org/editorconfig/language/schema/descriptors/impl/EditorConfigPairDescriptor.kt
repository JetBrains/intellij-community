// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors.impl

import com.intellij.psi.PsiElement
import org.editorconfig.language.psi.EditorConfigOptionValuePair
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptorVisitor
import org.editorconfig.language.schema.descriptors.EditorConfigMutableDescriptor

data class EditorConfigPairDescriptor(
  val first: EditorConfigDescriptor,
  val second: EditorConfigDescriptor,
  override val documentation: String?,
  override val deprecation: String?
) : EditorConfigMutableDescriptor {
  override val children = listOf(first, second)
  override var parent: EditorConfigDescriptor? = null
  override fun accept(visitor: EditorConfigDescriptorVisitor) = visitor.visitPair(this)

  init {
    (first as EditorConfigMutableDescriptor).parent = this
    (second as EditorConfigMutableDescriptor).parent = this
  }

  override fun matches(element: PsiElement) =
    element is EditorConfigOptionValuePair
}
