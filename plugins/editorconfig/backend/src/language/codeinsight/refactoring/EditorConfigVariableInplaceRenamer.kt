// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.refactoring

import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import com.intellij.util.Processor
import org.editorconfig.language.codeinsight.findusages.EditorConfigFindUsagesHandlerFactory
import org.editorconfig.language.codeinsight.findusages.EditorConfigFindVariableUsagesHandler
import org.editorconfig.language.util.EditorConfigIdentifierUtil.findIdentifiers

class EditorConfigVariableInplaceRenamer(elementToRename: PsiNamedElement, editor: Editor) :
  VariableInplaceRenamer(elementToRename, editor) {
  override fun collectRefs(referencesSearchScope: SearchScope?): Collection<PsiReference> {
    val describable = myElementToRename as? EditorConfigDescribableElement ?: return emptyList()
    val id = EditorConfigFindVariableUsagesHandler.getId(describable)
    val identifiers = findIdentifiers(describable.section, id, describable.text)
    return identifiers.mapNotNull(EditorConfigDescribableElement::getReference)
  }

  override fun checkLocalScope(): PsiElement? {
    val file = myElementToRename.containingFile
    val factory = EditorConfigFindUsagesHandlerFactory()
    if (factory.canFindUsages(myElementToRename)) {
      val handler = factory.createFindUsagesHandler(myElementToRename, false) ?: return null
      val sameFile = handler.processElementUsages(
        myElementToRename,
        Processor { it.file == file },
        FindUsagesOptions(myProject)
      )
      if (sameFile) return file
    }
    return super.checkLocalScope()
  }
}
