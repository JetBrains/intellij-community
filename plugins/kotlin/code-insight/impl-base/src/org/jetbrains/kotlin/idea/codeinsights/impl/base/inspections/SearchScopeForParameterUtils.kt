// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.search.isCheapEnoughToSearchConsideringOperators
import org.jetbrains.kotlin.idea.util.getAccessorNames
import org.jetbrains.kotlin.psi.KtParameter

/**
 * Returns a [SearchScope] to search a parameter reference in. If it's too expensive, returns null.
 */
@ApiStatus.Internal
fun getScopeToSearchParameterReferences(parameter: KtParameter): SearchScope? {
    val name = parameter.name ?: return null
    val useScope = parameter.useScope
    val restrictedScope = if (useScope is GlobalSearchScope) {
        val psiSearchHelper = PsiSearchHelper.getInstance(parameter.project)
        for (accessorName in parameter.getAccessorNames()) {
            val searchResult = psiSearchHelper.isCheapEnoughToSearchConsideringOperators(accessorName, useScope, null)
            when (searchResult) {
                PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES -> {
                    // Go on
                }

                else -> {
                    // Accessor in use: should remain a property
                    return null
                }
            }
        }
        // TOO_MANY_OCCURRENCES: too expensive
        // ZERO_OCCURRENCES: unused at all, reported elsewhere
        val searchResult = psiSearchHelper.isCheapEnoughToSearchConsideringOperators(name, useScope, null)
        if (searchResult != PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES) {
            return null
        }
        KotlinSourceFilterScope.projectSources(useScope, parameter.project)
    } else useScope
    return restrictedScope
}