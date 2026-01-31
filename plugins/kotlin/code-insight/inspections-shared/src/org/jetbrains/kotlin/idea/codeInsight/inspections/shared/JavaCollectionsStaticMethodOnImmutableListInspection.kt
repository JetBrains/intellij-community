// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.JavaCollectionsStaticMethodInspectionUtils.getMethodIfItsArgumentIsImmutableList
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.dotQualifiedExpressionVisitor

internal class JavaCollectionsStaticMethodOnImmutableListInspection :
    KotlinApplicableInspectionBase<KtDotQualifiedExpression, JavaCollectionsStaticMethodOnImmutableListInspection.Context>() {

    class Context(
        val methodName: String,
        val firstArg: KtValueArgument
    )

    override fun InspectionManager.createProblemDescriptor(
        element: KtDotQualifiedExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor = createProblemDescriptor(
        /* psiElement = */ element,
        /* rangeInElement = */
        rangeInElement,
        /* descriptionTemplate = */
        KotlinBundle.message("call.of.java.mutator.0.on.immutable.kotlin.collection.1", context.methodName, context.firstArg.text),
        /* highlightType = */
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        /* onTheFly = */
        false,
        /* ...fixes = */
        *LocalQuickFix.EMPTY_ARRAY
    )

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Context? {
        val (methodName, firstArg) = getMethodIfItsArgumentIsImmutableList(element) ?: return null
        return Context(methodName, firstArg)
    }

    override fun getApplicableRanges(element: KtDotQualifiedExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.callExpression?.calleeExpression }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = dotQualifiedExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }
}
