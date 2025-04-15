// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.schema.descriptors.impl

import com.intellij.psi.PsiElement
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigOptionValueIdentifier
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptorVisitor
import org.editorconfig.language.util.EditorConfigTextMatchingUtil
import org.jetbrains.annotations.Nls

object EditorConfigUnsetValueDescriptor : EditorConfigDescriptor {
  private const val text = "unset"
  override val deprecation: String? = null
  override val documentation: @Nls String
    get() = EditorConfigBundle["descriptor.unset.documentation"]

  override val parent: EditorConfigDescriptor
    get() = throw UnsupportedOperationException()

  override fun accept(visitor: EditorConfigDescriptorVisitor): Unit = visitor.visitUnset(this)
  override fun matches(element: PsiElement): Boolean =
    element is EditorConfigOptionValueIdentifier
    && element.parent is EditorConfigOption
    && EditorConfigTextMatchingUtil.textMatchesToIgnoreCase(element, text)
}
