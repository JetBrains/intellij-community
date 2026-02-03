// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.search.MethodTextOccurrenceProcessor
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.util.Processor
import org.jetbrains.plugins.groovy.lang.psi.util.getAccessorName
import org.jetbrains.plugins.groovy.lang.psi.util.getPropertyName

class GradleManagedPropertySetterReferenceSearcher : QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters>(true) {

  override fun processQuery(queryParameters: MethodReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    val method = queryParameters.method
    val containingClass = method.containingClass ?: return
    val propertyName = PropertyKind.GETTER.getPropertyName(method) ?: return
    val setterName = PropertyKind.SETTER.getAccessorName(propertyName)
    queryParameters.optimizer.searchWord(
      setterName, queryParameters.effectiveSearchScope, UsageSearchContext.IN_CODE, true, method,
      MethodTextOccurrenceProcessor(containingClass, queryParameters.isStrictSignatureSearch, method)
    )
  }
}
