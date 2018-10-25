// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors.impl

import com.intellij.psi.PsiElement
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptorVisitor
import org.editorconfig.language.schema.descriptors.EditorConfigMutableDescriptor

data class EditorConfigUnionDescriptor(
  override val children: List<EditorConfigDescriptor>,
  override val documentation: String?,
  override val deprecation: String?
) : EditorConfigMutableDescriptor {

  override var parent: EditorConfigDescriptor? = null

  override fun accept(visitor: EditorConfigDescriptorVisitor) = visitor.visitUnion(this)

  init {
    children.forEach { (it as EditorConfigMutableDescriptor).parent = this }
  }

  override fun matches(element: PsiElement) = children.any { it.matches(element) }
}
