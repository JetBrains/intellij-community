// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.MethodReferencesSearch.SearchParameters
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.Companion.canBeResolvedWithFrontEnd
import org.jetbrains.kotlin.idea.search.usagesSearch.buildProcessDelegationCallConstructorUsagesTask
import org.jetbrains.kotlin.idea.util.application.runReadAction

class KotlinConstructorDelegationCallReferenceSearcher : QueryExecutorBase<PsiReference, SearchParameters>() {
    override fun processQuery(queryParameters: SearchParameters, consumer: Processor<in PsiReference>) {
        runReadAction {
            val method = queryParameters.method
            if (!method.isConstructor) return@runReadAction null
            if (!method.canBeResolvedWithFrontEnd()) return@runReadAction null
            val searchScope = queryParameters.effectiveSearchScope

            method.buildProcessDelegationCallConstructorUsagesTask(searchScope) {
                it.calleeExpression?.mainReference?.let(consumer::process) ?: true
            }
        }?.invoke()
    }
}