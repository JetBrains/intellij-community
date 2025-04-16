// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors.impl

import com.intellij.editorconfig.common.syntax.psi.EditorConfigOption
import com.intellij.psi.PsiElement
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptorVisitor
import org.editorconfig.language.schema.descriptors.EditorConfigMutableDescriptor

data class EditorConfigOptionDescriptor(
  val key: EditorConfigDescriptor,
  val value: EditorConfigDescriptor,
  override val documentation: String?,
  override val deprecation: String?
) : EditorConfigMutableDescriptor {
  override val children: List<EditorConfigDescriptor> = listOf(key, value)

  override var parent: EditorConfigDescriptor?
    get() = null
    set(@Suppress("UNUSED_PARAMETER") value) = throw IllegalStateException()

  init {
    (key as EditorConfigMutableDescriptor).parent = this
    (value as EditorConfigMutableDescriptor).parent = this
  }

  override fun accept(visitor: EditorConfigDescriptorVisitor): Unit =
    visitor.visitOption(this)

  override fun matches(element: PsiElement): Boolean =
    element is EditorConfigOption
}
