// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.documentation

import com.intellij.editorconfig.common.syntax.EditorConfigLanguage
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.light.LightElement
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor

class EditorConfigDocumentationHolderElement(
  manager: PsiManager,
  val descriptor: EditorConfigDescriptor?
) : LightElement(manager, EditorConfigLanguage) {
  override fun toString(): String {
    return "EditorConfigDocumentationHolderElement(descriptor=$descriptor)"
  }

  override fun getText() : String {
    return descriptor?.getPresentableText() ?: ""
  }
}
