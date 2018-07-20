// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.indexedProperty

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField

class IndexedPropertyReferenceSearchExecutor : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {

  override fun execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>): Boolean {
    val field = queryParameters.elementToSearch
    if (field is GrField) {
      runReadAction {
        findIndexedPropertyMethods(field)
      }?.forEach { method ->
        MethodReferencesSearch.searchOptimized(method, queryParameters.effectiveSearchScope, true, queryParameters.optimizer, consumer)
      }
    }
    return true
  }
}
