// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.getDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigReferenceDescriptor

class EditorConfigRenameHandler : VariableInplaceRenameHandler() {
  override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean {
    if (element !is EditorConfigDescribableElement) return false
    if (PsiElementRenameHandler.isVetoed(element)) return false
    return when (element.getDescriptor(false)) {
      is EditorConfigDeclarationDescriptor,
      is EditorConfigReferenceDescriptor ->
        editor.settings.isVariableInplaceRenameEnabled
      else -> false
    }
  }

  override fun createRenamer(elementToRename: PsiElement, editor: Editor): VariableInplaceRenamer {
    return EditorConfigVariableInplaceRenamer(elementToRename as PsiNamedElement, editor)
  }
}
