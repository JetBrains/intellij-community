// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.reference

import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import org.editorconfig.language.util.EditorConfigIdentifierUtil

class EditorConfigIdentifierReference(
  element: EditorConfigDescribableElement,
  private val id: String
) : PsiPolyVariantReferenceBase<EditorConfigDescribableElement>(element) {
  override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> {
    val declarations = EditorConfigIdentifierUtil.findDeclarations(element.section, id, element.text)
    return declarations
      .map(::PsiElementResolveResult)
      .toTypedArray()
  }
}
