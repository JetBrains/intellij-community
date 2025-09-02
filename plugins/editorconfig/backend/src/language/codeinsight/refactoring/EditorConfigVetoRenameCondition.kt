// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.refactoring

import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import org.editorconfig.language.schema.descriptors.getDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigReferenceDescriptor

class EditorConfigVetoRenameCondition : Condition<PsiElement> {
  override fun value(element: PsiElement): Boolean {
    if (element !is EditorConfigDescribableElement) return false
    return when (element.getDescriptor(false)) {
      is EditorConfigDeclarationDescriptor -> false
      is EditorConfigReferenceDescriptor -> false
      else -> true
    }
  }
}
