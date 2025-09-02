// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.findusages

import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.editorconfig.common.syntax.psi.EditorConfigIdentifierElement
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.psi.PsiElement
import org.editorconfig.language.codeinsight.findusages.EditorConfigFindVariableUsagesHandler.Companion.getId

class EditorConfigFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
  override fun canFindUsages(element: PsiElement): Boolean = element is EditorConfigIdentifierElement
  override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
    if (element !is EditorConfigDescribableElement) return null
    if (getId(element) != null) return EditorConfigFindVariableUsagesHandler(element)
    return EditorConfigDescriptorBasedFindUsagesHandler(element)
  }
}
