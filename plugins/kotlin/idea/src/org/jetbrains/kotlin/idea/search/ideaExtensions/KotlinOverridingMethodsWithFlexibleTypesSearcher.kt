// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.psi.PsiMethod
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.search.declarationsSearch.findOverridingMethodsInKotlin
import org.jetbrains.kotlin.idea.util.application.runReadAction

class KotlinOverridingMethodsWithFlexibleTypesSearcher : QueryExecutor<PsiMethod, OverridingMethodsSearch.SearchParameters> {
    override fun execute(p: OverridingMethodsSearch.SearchParameters, consumer: Processor<in PsiMethod>): Boolean {
        val method = p.method
        if (method is KtLightMethod) return true

        // Java overriding method search can't find overloads with flexible types, so we do additional search for such methods
        val parentClass = runReadAction { method.containingClass } ?: return true

        return findOverridingMethodsInKotlin(parentClass, method, p, consumer)
    }

}
