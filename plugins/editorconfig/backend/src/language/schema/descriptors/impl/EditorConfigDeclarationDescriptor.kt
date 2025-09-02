// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors.impl

import com.intellij.editorconfig.common.syntax.psi.EditorConfigFlatOptionKey
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValueIdentifier
import com.intellij.editorconfig.common.syntax.psi.EditorConfigQualifiedKeyPart
import com.intellij.psi.PsiElement
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptorVisitor
import org.editorconfig.language.schema.descriptors.EditorConfigMutableDescriptor

data class EditorConfigDeclarationDescriptor(
  val id: String,
  val needsReferences: Boolean,
  val isRequired: Boolean,
  override val documentation: String?,
  override val deprecation: String?
) : EditorConfigMutableDescriptor {
  override var parent: EditorConfigDescriptor? = null
  override fun accept(visitor: EditorConfigDescriptorVisitor): Unit = visitor.visitDeclaration(this)

  override fun matches(element: PsiElement): Boolean =
    element is EditorConfigFlatOptionKey
    || element is EditorConfigQualifiedKeyPart
    || element is EditorConfigOptionValueIdentifier
}
