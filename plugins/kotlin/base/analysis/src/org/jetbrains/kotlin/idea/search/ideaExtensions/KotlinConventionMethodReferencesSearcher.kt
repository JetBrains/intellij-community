// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.Companion.canBeResolvedWithFrontEnd
import org.jetbrains.kotlin.idea.search.usagesSearch.operators.OperatorReferenceSearcher

class KotlinConventionMethodReferencesSearcher : QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters>() {
    override fun processQuery(queryParameters: MethodReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        runReadAction {
            val method = queryParameters.method
            if (!method.canBeResolvedWithFrontEnd()) return@runReadAction null

            val operatorSearcher = OperatorReferenceSearcher.create(
                queryParameters.method,
                queryParameters.effectiveSearchScope,
                consumer,
                queryParameters.optimizer,
                KotlinReferencesSearchOptions(acceptCallableOverrides = true)
            )
            operatorSearcher
        }?.run()
    }
}
