// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.psi.PsiMethod
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.psi.KtCallableDeclaration

class KotlinOverridingMethodsWithGenericsSearcher : QueryExecutor<PsiMethod, OverridingMethodsSearch.SearchParameters> {
    override fun execute(p: OverridingMethodsSearch.SearchParameters, consumer: Processor<in PsiMethod>): Boolean {
        val method = p.method
        if (method !is KtLightMethod) return true

        val declaration = method.kotlinOrigin as? KtCallableDeclaration ?: return true

        val callDescriptor = runReadAction { declaration.unsafeResolveToDescriptor() }
        if (callDescriptor !is CallableDescriptor) return true

        // Java overriding method search can't find overloads with primitives types, so
        // we do additional search for such methods.
        if (!callDescriptor.valueParameters.any { it.type.constructor.declarationDescriptor is TypeParameterDescriptor }) return true

        val parentClass = runReadAction { method.containingClass }
        return findOverridingMethodsInKotlin(parentClass, declaration, p, consumer)
    }
}
