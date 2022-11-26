// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.findusages

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigReferenceDescriptor
import org.editorconfig.language.util.EditorConfigTextMatchingUtil.textMatchesToIgnoreCase
import org.editorconfig.language.util.EditorConfigVfsUtil

class EditorConfigFindVariableUsagesHandler(element: EditorConfigDescribableElement) : FindUsagesHandler(element) {
  override fun processElementUsages(element: PsiElement, processor: Processor<in UsageInfo>, options: FindUsagesOptions) =
    runReadAction {
      getId(element)?.let { id -> findAllUsages(element, id) }
        ?.map(::UsageInfo)
        ?.all(processor::process) == true
    }

  override fun findReferencesToHighlight(target: PsiElement, searchScope: SearchScope) =
    runReadAction {
      if (searchScope !is LocalSearchScope) return@runReadAction emptyList<PsiReference>()
      val id = getId(target) ?: return@runReadAction emptyList<PsiReference>()
      searchScope.scope.asSequence()
        .flatMap { PsiTreeUtil.findChildrenOfType(it, EditorConfigDescribableElement::class.java).asSequence() }
        .filter { matches(it, id, target) }
        .mapNotNull(EditorConfigDescribableElement::getReference)
        .toList()
    }

  private fun findAllUsages(element: PsiElement, id: String) =
    EditorConfigVfsUtil.getEditorConfigFiles(element.project)
      .asSequence()
      .map(PsiManager.getInstance(element.project)::findFile)
      .flatMap { PsiTreeUtil.findChildrenOfType(it, EditorConfigDescribableElement::class.java).asSequence() }
      .filter { matches(it, id, element) }

  private fun matches(element: PsiElement, id: String, template: PsiElement): Boolean {
    if (element !is EditorConfigDescribableElement) return false
    if (!textMatchesToIgnoreCase(element, template)) return false
    return when (val descriptor = element.getDescriptor(false)) {
      is EditorConfigDeclarationDescriptor -> descriptor.id == id
      is EditorConfigReferenceDescriptor -> descriptor.id == id
      else -> false
    }
  }

  companion object {
    fun getId(element: PsiElement): String? {
      if (element !is EditorConfigDescribableElement) return null
      return when (val descriptor = element.getDescriptor(false)) {
        is EditorConfigDeclarationDescriptor -> descriptor.id
        is EditorConfigReferenceDescriptor -> descriptor.id
        else -> null
      }
    }
  }
}
