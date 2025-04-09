// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors.impl

import com.intellij.psi.PsiElement
import org.editorconfig.language.psi.EditorConfigQualifiedKeyPart
import org.editorconfig.language.psi.EditorConfigQualifiedOptionKey
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptorVisitor
import org.editorconfig.language.schema.descriptors.EditorConfigMutableDescriptor
import org.editorconfig.language.util.EditorConfigTextMatchingUtil

data class EditorConfigQualifiedKeyDescriptor(
  override val children: List<EditorConfigDescriptor>,
  override val documentation: String?,
  override val deprecation: String?
) : EditorConfigMutableDescriptor {
  override var parent: EditorConfigDescriptor? = null

  init {
    children.forEach { (it as EditorConfigMutableDescriptor).parent = this }
  }

  override fun accept(visitor: EditorConfigDescriptorVisitor) = visitor.visitQualifiedKey(this)

  override fun matches(element: PsiElement): Boolean {
    if (element !is EditorConfigQualifiedOptionKey) return false
    val parts = element.qualifiedKeyPartList
    if (children.size != parts.size) return false
    return children.zip(parts).all { (descriptor, part) -> matches(descriptor, part) }
  }

  private fun matches(descriptor: EditorConfigDescriptor, element: EditorConfigQualifiedKeyPart): Boolean = when (descriptor) {
    is EditorConfigConstantDescriptor -> EditorConfigTextMatchingUtil.textMatchesToIgnoreCase(element, descriptor.text)
    is EditorConfigUnionDescriptor -> descriptor.children.any { matches(it, element) }
    is EditorConfigDeclarationDescriptor -> true
    else -> false
  }
}
