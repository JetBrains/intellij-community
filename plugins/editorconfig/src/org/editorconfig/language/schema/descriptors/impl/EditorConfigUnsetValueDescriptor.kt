// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors.impl

import com.intellij.psi.PsiElement
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigOptionValueIdentifier
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptorVisitor
import org.editorconfig.language.util.EditorConfigTextMatchingUtil

object EditorConfigUnsetValueDescriptor : EditorConfigDescriptor {
  private const val text = "unset"
  override val deprecation: String? = null
  override val documentation
    get() = EditorConfigBundle["descriptor.unset.documentation"]

  override val parent: EditorConfigDescriptor?
    get() = throw UnsupportedOperationException()

  override fun accept(visitor: EditorConfigDescriptorVisitor) = visitor.visitUnset(this)
  override fun matches(element: PsiElement) =
    element is EditorConfigOptionValueIdentifier
    && element.parent is EditorConfigOption
    && EditorConfigTextMatchingUtil.textMatchesToIgnoreCase(element, text)
}
