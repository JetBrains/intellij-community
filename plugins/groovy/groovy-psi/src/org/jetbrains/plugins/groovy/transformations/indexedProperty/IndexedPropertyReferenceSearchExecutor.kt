/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.transformations.indexedProperty

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField

class IndexedPropertyReferenceSearchExecutor : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {

  override fun execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<PsiReference>): Boolean {
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
