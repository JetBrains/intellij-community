// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.canBeResolvedWithFrontEnd
import org.jetbrains.kotlin.idea.search.usagesSearch.operators.OperatorReferenceSearcher

class KotlinConventionMethodReferencesSearcher : QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters>() {
    override fun processQuery(queryParameters: MethodReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        if (queryParameters is KotlinMethodReferencesSearchParameters &&
            !queryParameters.kotlinOptions.searchForComponentConventions &&
            !queryParameters.kotlinOptions.searchForOperatorConventions) {
            return
        }
        runReadAction {
            val method = queryParameters.method

            val effectiveSearchScope = queryParameters.effectiveSearchScope
            if (effectiveSearchScope is LocalSearchScope && effectiveSearchScope.scope.size == 1) {
                // Search in a number of known files is a common task required by many code analysis features, most of the time Java runs a
                // search of a method inside a single class or a file. Such searches have `effectiveSearchScope` which is instance of
                // `LocalSearchScope` and 1 element as a scope. Since number of possible elements in `LocalSearchScope` is not limited
                // we only optimize a case with exactly 1 PsiElement.
                val scopeLanguage = effectiveSearchScope.scope[0].containingFile?.language
                if (!KotlinLanguage.INSTANCE.`is`(scopeLanguage)) {
                    val methodLanguage = queryParameters.method.containingFile?.language
                    if (!KotlinLanguage.INSTANCE.`is`(methodLanguage)) {
                        return@runReadAction null
                    }
                }
            }

            if (!method.canBeResolvedWithFrontEnd()) return@runReadAction null

            val operatorSearcher = OperatorReferenceSearcher.create(
                queryParameters.method,
                effectiveSearchScope,
                consumer,
                queryParameters.optimizer,
                KotlinReferencesSearchOptions(acceptCallableOverrides = true)
            )
            operatorSearcher
        }?.run()
    }
}
