// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.util.Processor
import org.jetbrains.plugins.groovy.findUsages.GroovyScopeUtil.restrictScopeToGroovyFiles
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils

/**
 * author ven
 */
class PropertyMethodReferenceSearchExecutor : QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters>(true) {

  override fun processQuery(queryParameters: MethodReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    val method = queryParameters.method

    val propertyName: String? = if (GdkMethodUtil.isCategoryMethod(method, null, null, PsiSubstitutor.EMPTY)) {
      val cat = GrGdkMethodImpl.createGdkMethod(method, false, null)
      GroovyPropertyUtils.getPropertyName(cat as PsiMethod)
    }
    else {
      GroovyPropertyUtils.getPropertyName(method)
    }

    if (propertyName == null) return

    val onlyGroovyFiles = restrictScopeToGroovyFiles(queryParameters.effectiveSearchScope, GroovyScopeUtil.getEffectiveScope(method))

    queryParameters.optimizer.searchWord(propertyName, onlyGroovyFiles, UsageSearchContext.IN_CODE, true, method)

    if (!GroovyPropertyUtils.isPropertyName(propertyName)) {
      queryParameters.optimizer.searchWord(
        StringUtil.decapitalize(propertyName)!!, onlyGroovyFiles, UsageSearchContext.IN_CODE, true, method
      )
    }
  }
}
