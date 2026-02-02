// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.JavaCollectionsStaticMethodInspectionUtils.getMethodIfItsArgumentIsMutableList
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.dotQualifiedExpressionVisitor

// In K2, it's ReplaceJavaStaticMethodWithKotlinAnalogInspection
internal class JavaCollectionsStaticMethodInspection :
    KotlinApplicableInspectionBase<KtDotQualifiedExpression, JavaCollectionsStaticMethodInspection.Context>() {

    internal class Context(
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
        KotlinBundle.message("java.collections.static.method.call.should.be.replaced.with.kotlin.stdlib"),
        /* highlightType = */
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        /* onTheFly = */
        false,
        /* ...fixes = */
        ReplaceWithStdLibFix(context.methodName, context.firstArg.text)
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = dotQualifiedExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Context? {
        val (methodName, firstArg) = getMethodIfItsArgumentIsMutableList(element) ?: return null
        return Context(methodName, firstArg)
    }

    override fun getApplicableRanges(element: KtDotQualifiedExpression): List<TextRange> =
        ApplicabilityRange.self(element)

    private class ReplaceWithStdLibFix(private val methodName: String, private val receiver: String) :
        KotlinModCommandQuickFix<KtDotQualifiedExpression>() {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.std.lib.fix.text", receiver, methodName)

        override fun applyFix(
            project: Project,
            element: KtDotQualifiedExpression,
            updater: ModPsiUpdater
        ) {
            val callExpression = element.callExpression ?: return
            val valueArguments = callExpression.valueArguments
            val firstArg = valueArguments.getOrNull(0)?.getArgumentExpression() ?: return
            val secondArg = valueArguments.getOrNull(1)?.getArgumentExpression()
            val factory = KtPsiFactory(project)
            val newExpression = if (secondArg != null) {
                if (methodName == "sort") {
                    factory.createExpressionByPattern("$0.sortWith(Comparator $1)", firstArg, secondArg.text)
                } else {
                    factory.createExpressionByPattern("$0.$methodName($1)", firstArg, secondArg)
                }
            } else {
                factory.createExpressionByPattern("$0.$methodName()", firstArg)
            }
            element.replace(newExpression)
        }
    }
}