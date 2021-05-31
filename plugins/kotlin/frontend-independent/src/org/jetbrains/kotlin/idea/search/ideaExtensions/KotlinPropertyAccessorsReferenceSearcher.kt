// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.util.Processor
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.idea.search.restrictToKotlinSources
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor

class KotlinPropertyAccessorsReferenceSearcher : QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters>() {
    override fun processQuery(queryParameters: MethodReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        runReadAction {
            val method = queryParameters.method
            val onlyKotlinFiles = queryParameters.effectiveSearchScope.restrictToKotlinSources()
            if (onlyKotlinFiles == GlobalSearchScope.EMPTY_SCOPE) return@runReadAction null

            val propertyNames = propertyNames(method)
            val function = {
                for (propertyName in propertyNames) {
                    queryParameters.optimizer!!.searchWord(
                        propertyName,
                        onlyKotlinFiles,
                        UsageSearchContext.IN_CODE,
                        true,
                        method
                    )
                }
            }
            function
        }?.invoke()
    }

    private fun propertyNames(method: PsiMethod): List<String> {
        val unwrapped = method.namedUnwrappedElement
        if (unwrapped is KtProperty) {
            return listOfNotNull(unwrapped.getName())
        }

        return SyntheticJavaPropertyDescriptor.propertyNamesByAccessorName(Name.identifier(method.name)).map(Name::asString)
    }
}
