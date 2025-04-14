// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors.impl

import com.intellij.psi.PsiElement
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptorVisitor
import org.editorconfig.language.schema.descriptors.EditorConfigMutableDescriptor

data class EditorConfigNumberDescriptor(
  override val documentation: String?,
  override val deprecation: String?
) : EditorConfigMutableDescriptor {
  override var parent: EditorConfigDescriptor? = null
  override fun accept(visitor: EditorConfigDescriptorVisitor) = visitor.visitNumber(this)

  override fun matches(element: PsiElement): Boolean =
    element.text.asEditorConfigInt() != null

  private companion object {
    private const val ALLOW_NEGATIVE_INTEGERS = false
    private val POSITIVE_NUMBER_REGEX = "\\d+".toRegex()
    private val NUMBER_REGEX = "-?\\d+".toRegex()

    private fun String.asEditorConfigInt(): Int? {
      val regex =
        if (ALLOW_NEGATIVE_INTEGERS) NUMBER_REGEX
        else POSITIVE_NUMBER_REGEX

      if (!regex.matches(this)) return null
      return toIntOrNull()
    }
  }
}
