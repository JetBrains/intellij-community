// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.reference

import com.intellij.psi.PsiElementResolveResult
import org.editorconfig.language.psi.EditorConfigFlatOptionKey

class EditorConfigFlatOptionKeyResolveResult(element: EditorConfigFlatOptionKey) : PsiElementResolveResult(element) {
  override fun getElement() = super.getElement() as EditorConfigFlatOptionKey
}
