// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.MethodReferencesSearch.SearchParameters
import com.intellij.util.Processor
import org.jetbrains.plugins.groovy.findUsages.GroovyScopeUtil.restrictScopeToGroovyFiles
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMethodReferenceExpressionImpl

/**
 * Searches for `ClassName::new` references.
 */
class GrNewMethodsReferencesSearcher : QueryExecutorBase<PsiReference, SearchParameters>(true) {

  override fun processQuery(queryParameters: SearchParameters, consumer: Processor<in PsiReference>) {
    val method = queryParameters.method
    if (!method.isConstructor) return

    queryParameters.optimizer.searchWord(
      GrMethodReferenceExpressionImpl.CONSTRUCTOR_REFERENCE_NAME,
      restrictScopeToGroovyFiles(queryParameters.effectiveSearchScope),
      UsageSearchContext.IN_CODE,
      true,
      method,
      TextProcessor(method)
    )
  }

  private class TextProcessor(private val target: PsiMethod) : RequestResultProcessor() {

    override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
      val methodReference = element as? GrMethodReferenceExpressionImpl ?: return true
      val nameElement = methodReference.referenceNameElement ?: return true
      val absoluteOffset = element.textRange.startOffset + offsetInElement
      if (absoluteOffset !in nameElement.textRange) return true
      if (!methodReference.isReferenceTo(target)) return true
      return consumer.process(methodReference)
    }
  }
}
