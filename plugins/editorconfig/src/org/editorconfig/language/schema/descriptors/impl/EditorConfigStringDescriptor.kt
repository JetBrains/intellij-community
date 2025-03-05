// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors.impl

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptorVisitor
import org.editorconfig.language.schema.descriptors.EditorConfigMutableDescriptor

data class EditorConfigStringDescriptor(
  override val documentation: String?,
  override val deprecation: String?,
  val pattern : String? = null
) : EditorConfigMutableDescriptor {
  override var parent: EditorConfigDescriptor? = null
  override fun accept(visitor: EditorConfigDescriptorVisitor) = visitor.visitString(this)
  override fun matches(element: PsiElement): Boolean {
    if (element.children.isNotEmpty()) return false
    return when {
      pattern != null -> element.text.matches(pattern.toRegex())
      else -> StringUtil.isJavaIdentifier(element.text)
    }
  }
}
