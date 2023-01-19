/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.Processor
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport

fun findOverridingMethodsInKotlin(
    parentClass: PsiClass,
    baseElement: PsiNamedElement,
    parameters: OverridingMethodsSearch.SearchParameters,
    consumer: Processor<in PsiMethod>,
): Boolean = ClassInheritorsSearch.search(parentClass, parameters.scope, true).forEach(Processor { inheritor: PsiClass ->
    val found = runReadAction { findOverridingMethod(inheritor, baseElement) }

    found == null || (consumer.process(found) && parameters.isCheckDeep)
})

private fun findOverridingMethod(inheritor: PsiClass, baseElement: PsiNamedElement): PsiMethod? {
    // Leave Java classes search to JavaOverridingMethodsSearcher
    if (inheritor !is KtLightClass) return null

    val name = baseElement.name
    val methodsByName = inheritor.findMethodsByName(name, false)

    for (lightMethodCandidate in methodsByName) {
        val kotlinOrigin = (lightMethodCandidate as? KtLightMethod)?.kotlinOrigin ?: continue
        if (KotlinSearchUsagesSupport.getInstance(inheritor.project).isCallableOverride(kotlinOrigin, baseElement)) {
            return lightMethodCandidate
        }
    }

    return null
}