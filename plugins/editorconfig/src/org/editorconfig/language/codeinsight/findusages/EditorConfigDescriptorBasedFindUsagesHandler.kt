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
import org.editorconfig.language.util.EditorConfigVfsUtil

class EditorConfigDescriptorBasedFindUsagesHandler(element: EditorConfigDescribableElement) : FindUsagesHandler(element) {
  override fun processElementUsages(element: PsiElement, processor: Processor<in UsageInfo>, options: FindUsagesOptions) = runReadAction {
    element as? EditorConfigDescribableElement ?: return@runReadAction false
    val descriptor = element.getDescriptor(false) ?: return@runReadAction false
    EditorConfigVfsUtil
      .getEditorConfigFiles(project)
      .asSequence()
      .map(PsiManager.getInstance(project)::findFile)
      .flatMap { PsiTreeUtil.findChildrenOfType(it, EditorConfigDescribableElement::class.java).asSequence() }
      .filter { it.getDescriptor(false) == descriptor }
      .map(::UsageInfo)
      .forEach {
        if (!processor.process(it)) return@runReadAction false
      }
    return@runReadAction true
  }

  override fun findReferencesToHighlight(target: PsiElement, searchScope: SearchScope) = runReadAction {
    searchScope as? LocalSearchScope ?: return@runReadAction emptyList<PsiReference>()
    target as? EditorConfigDescribableElement ?: return@runReadAction emptyList<PsiReference>()
    val descriptor = target.getDescriptor(false) ?: return@runReadAction emptyList<PsiReference>()
    searchScope.scope.asSequence()
      .flatMap { PsiTreeUtil.findChildrenOfType(it, EditorConfigDescribableElement::class.java).asSequence() }
      .filter { it.getDescriptor(false) == descriptor }
      .mapNotNull(EditorConfigDescribableElement::getReference)
      .toList()
  }
}
