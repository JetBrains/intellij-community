// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.search

import com.intellij.devkit.apiDump.lang.ADFileType
import com.intellij.devkit.apiDump.lang.psi.ADClassDeclaration
import com.intellij.devkit.apiDump.lang.psi.ADClassHeader
import com.intellij.devkit.apiDump.lang.psi.ADConstructor
import com.intellij.devkit.apiDump.lang.psi.ADTypeReference
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.util.childrenOfType
import com.intellij.util.Processor

/**
 * Searches for the containing class of the target constructor in api-dump(.*).txt's and processes constructors of the found api declaration.
 */
internal class ADConstructorReferenceSearcher : QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters>(true) {
  override fun processQuery(queryParameters: MethodReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    if (!(Registry.`is`("intellij.devkit.api.dump.find.usages"))) return

    val targetConstructor = queryParameters.method.takeIf { it.isConstructor } ?: return
    if (!IntelliJProjectUtil.isIntelliJPlatformProject(queryParameters.project)) return

    val targetClass = targetConstructor.containingClass ?: return
    val targetClassFQN = targetClass.qualifiedName ?: return

    val adFilesInScope = PsiSearchScopeUtil.restrictScopeTo(queryParameters.effectiveSearchScope, ADFileType.INSTANCE)
    queryParameters.optimizer.searchWord(
      /* word = */ targetClassFQN,
      /* searchScope = */ adFilesInScope,
      /* searchContext = */ UsageSearchContext.ANY,
      /* caseSensitive = */ true,
      /* searchTarget = */ targetClass,
      /* processor = */ object : RequestResultProcessor() {
      override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
        if (element !is ADTypeReference) return true
        val classDeclaration = (element.parent as? ADClassHeader)?.parent as? ADClassDeclaration ?: return true
        val matchingConstructor = classDeclaration.childrenOfType<ADConstructor>().firstOrNull { constructor ->
          constructor.constructorReference.reference?.isReferenceTo(targetConstructor) == true
        } ?: return true
        return consumer.process(matchingConstructor.constructorReference.reference!!)
      }
    })
  }
}