// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.canPlaceAfterSimpleNameEntry
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.util.match

/**
 * Utility functions for transforming index-based loops to collection loops.
 */
@ApiStatus.Internal
object LoopToCollectionTransformUtils {

    /**
     * Finds a `.get()` method call that contains the given usage element as its argument.
     *
     * @param usage the usage element (typically a reference to the loop parameter)
     * @return the [KtDotQualifiedExpression] representing the `.get()` call, or null if not found
     */
    fun findGetCallAccess(usage: PsiElement): KtDotQualifiedExpression? {
        val callExpr = usage.parents.match(KtValueArgument::class, KtValueArgumentList::class, last = KtCallExpression::class)
            ?: return null
        val isGetCall = callExpr.calleeExpression?.text == "get" && callExpr.valueArguments.size == 1
        return (callExpr.parent as? KtDotQualifiedExpression).takeIf { isGetCall }
    }

    /**
     * Transforms an index-based loop to a collection-based loop by:
     * 1. Replacing the loop parameter with the specified element name
     * 2. Replacing all indexed access expressions (both arr[i] and arr.get(i)) with direct element references
     * 3. Replacing the loop range with the collection expression
     *
     * @param project the current project
     * @param indexedAccesses indexed access expressions to replace with element references (KtArrayAccessExpression or KtDotQualifiedExpression for .get())
     * @param loopParameter the original loop parameter
     * @param loopRange the original loop range
     * @param newLoopRange the new collection expression to iterate over
     * @param elementName the name for the new loop variable (should be pre-validated to avoid conflicts)
     */
    fun transformLoop(
        project: Project,
        indexedAccesses: List<KtExpression>,
        loopParameter: KtParameter,
        loopRange: KtExpression,
        newLoopRange: KtExpression,
        elementName: String
    ) {
        val factory = KtPsiFactory(project)

        val newParameter = factory.createLoopParameter(elementName)

        // Replace all indexed access expressions with direct element references
        indexedAccesses.forEach { accessExpr ->
            replaceIndexedAccessWithElement(factory, accessExpr, elementName)
        }

        loopParameter.replace(newParameter)
        loopRange.replace(newLoopRange)
    }

    /**
     * Replaces an indexed access expression with a simple element reference.
     * Handles both bracket notation (arr[i]) and .get() method calls (arr.get(i)).
     * When inside a string template ${...}, creates the simplified $name form directly if possible.
     */
    private fun replaceIndexedAccessWithElement(factory: KtPsiFactory, accessExpr: KtExpression, elementName: String) {
        val blockEntry = accessExpr.parent as? KtBlockStringTemplateEntry
        if (blockEntry != null && canPlaceAfterSimpleNameEntry(blockEntry.nextSibling)) {
            blockEntry.replace(factory.createSimpleNameStringTemplateEntry(elementName))
        } else {
            accessExpr.replace(factory.createExpression(elementName))
        }
    }

    /**
     * Transforms an index-based loop to a withIndex() loop by:
     * 1. Replacing the loop parameter with a destructuring declaration (indexName, elementName)
     * 2. Replacing indexed access expressions with direct element references
     * 3. Replacing the loop range with collection.withIndex()
     * @param project the current project
     * @param indexedAccesses indexed access expressions to replace with element references (KtArrayAccessExpression or KtDotQualifiedExpression for .get())
     * @param loopParameter the original loop parameter (the index variable)
     * @param loopRange the original loop range
     * @param collectionExpression the collection to iterate with withIndex()
     * @param elementName the name for the new element variable
     */
    fun transformLoopWithIndex(
        project: Project,
        indexedAccesses: List<KtExpression>,
        loopParameter: KtParameter,
        loopRange: KtExpression,
        collectionExpression: KtExpression,
        elementName: String
    ) {
        val factory = KtPsiFactory(project)

        val indexName = loopParameter.name ?: "index"
        val newParameter = factory.createDestructuringParameter("($indexName, $elementName)")
        val newLoopRange = factory.createExpressionByPattern("$0.withIndex()", collectionExpression)

        // Replace all indexed access expressions with direct element references
        indexedAccesses.forEach { accessExpr ->
            replaceIndexedAccessWithElement(factory, accessExpr, elementName)
        }

        loopParameter.replace(newParameter)
        loopRange.replace(newLoopRange)
    }
}