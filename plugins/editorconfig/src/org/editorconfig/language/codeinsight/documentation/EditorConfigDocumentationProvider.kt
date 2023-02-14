// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.documentation

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.getParentOfType
import org.jetbrains.annotations.Nls
import kotlin.math.max

class EditorConfigDocumentationProvider : DocumentationProvider {
  override fun generateDoc(element: PsiElement, originalElement: PsiElement?): @Nls String? {
    if (element !is EditorConfigDocumentationHolderElement) return null
    return element.descriptor?.documentation
  }

  override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): @Nls String? {
    if (element !is EditorConfigDocumentationHolderElement) return null
    return element.descriptor?.documentation
  }

  override fun getDocumentationElementForLookupItem(
    psiManager: PsiManager,
    lookupElement: Any,
    contextElement: PsiElement
  ): PsiElement? = when (lookupElement) {
    is EditorConfigDescriptor -> EditorConfigDocumentationHolderElement(psiManager, lookupElement)
    else -> null
  }

  override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?, targetOffset: Int): PsiElement? {
    contextElement ?: return null
    if (contextElement !is PsiWhiteSpace) {
      val describable = contextElement.getParentOfType<EditorConfigDescribableElement>()
      val descriptor = describable?.getDescriptor(false)
      return EditorConfigDocumentationHolderElement(file.manager, descriptor)
    }

    val offset = max(0, targetOffset - 1)
    val psiBeforeCaret = file.findElementAt(offset)
    val describable = psiBeforeCaret?.getParentOfType<EditorConfigDescribableElement>()
    val descriptor = describable?.getDescriptor(false) ?: return null
    return EditorConfigDocumentationHolderElement(file.manager, descriptor)
  }
}
