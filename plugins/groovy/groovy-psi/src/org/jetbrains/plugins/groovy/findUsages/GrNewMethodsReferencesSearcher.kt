// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMethodReferenceExpressionImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMethodReferenceExpressionImpl.Companion.CONSTRUCTOR_REFERENCE_NAME

/**
 * Searches for `ClassName::new` references.
 */
class GrNewMethodsReferencesSearcher : QueryExecutorBase<PsiReference, SearchParameters>(true) {

  override fun processQuery(queryParameters: SearchParameters, consumer: Processor<in PsiReference>) {
    val method = queryParameters.method
    if (!method.isConstructor) return

    val clazz = method.containingClass ?: return
    val name = clazz.name ?: return
    queryParameters.optimizer.searchWord(
      name,
      restrictScopeToGroovyFiles(queryParameters.effectiveSearchScope),
      UsageSearchContext.IN_CODE,
      true,
      clazz,
      TextProcessor(method)
    )
  }

  private class TextProcessor(private val target: PsiMethod) : RequestResultProcessor() {

    override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
      val qualifier = element as? GrReferenceExpression ?: return true
      val methodReference = element.parent as? GrMethodReferenceExpressionImpl ?: return true
      if (methodReference.referenceName != CONSTRUCTOR_REFERENCE_NAME) return true

      val nameElement = qualifier.referenceNameElement ?: return true
      val absoluteOffset = element.textRange.startOffset + offsetInElement
      if (absoluteOffset !in nameElement.textRange) return true

      if (!methodReference.isReferenceTo(target)) return true
      return consumer.process(methodReference)
    }
  }
}
