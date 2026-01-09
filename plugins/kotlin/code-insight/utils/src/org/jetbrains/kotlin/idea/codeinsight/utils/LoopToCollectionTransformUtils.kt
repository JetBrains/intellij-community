// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.util.match
import org.jetbrains.annotations.ApiStatus

/**
 * Utility functions for transforming index-based loops to collection loops.
 */
@ApiStatus.Internal
object LoopToCollectionTransformUtils {

    /**
     * Data class representing the result of finding loop usage patterns.
     */
    data class LoopUsageInfo(
        val paramElement: KtParameter,
        val usageElement: PsiElement,
        val arrayAccessElement: KtArrayAccessExpression
    )

    /**
     * Finds the usage pattern for a loop parameter where it's used as an array index exactly once.
     * 
     * @param loopParameter the loop parameter (e.g., `i` in `for (i in 0..<size)`)
     * @return LoopUsageInfo if the parameter is used exactly once as an array index, null otherwise
     */
    fun findSingleArrayAccessUsage(loopParameter: KtParameter): LoopUsageInfo? {
        val paramElement = loopParameter.originalElement ?: return null
        val usageElement = ReferencesSearch.search(paramElement).findAll().singleOrNull()?.element ?: return null
        val arrayAccessElement = usageElement.parents.match(KtContainerNode::class, last = KtArrayAccessExpression::class) ?: return null
        
        return LoopUsageInfo(paramElement as KtParameter, usageElement, arrayAccessElement)
    }

    /**
     * Transforms an index-based loop to a collection-based loop with multiple array access usages by:
     * 1. Replacing the loop parameter with "element" 
     * 2. Replacing all array access expressions with direct element references
     * 3. Replacing the loop range with the collection expression
     * 
     * @param project the current project
     * @param usageInfos information about all loop usage patterns
     * @param loopParameter the original loop parameter
     * @param loopRange the original loop range
     * @param newLoopRange the new collection expression to iterate over
     */
    fun transformLoop(
        project: Project,
        usageInfos: List<LoopUsageInfo>,
        loopParameter: KtParameter,
        loopRange: KtExpression,
        newLoopRange: KtExpression
    ) {
        val factory = KtPsiFactory(project)
        val newParameter = factory.createLoopParameter("element")
        val newReferenceExpression = factory.createExpression("element")
        
        // Replace all array access expressions with direct element references
        usageInfos.forEach { usageInfo ->
            usageInfo.arrayAccessElement.replace(newReferenceExpression.copy())
        }
        
        loopParameter.replace(newParameter)
        loopRange.replace(newLoopRange)
    }
}