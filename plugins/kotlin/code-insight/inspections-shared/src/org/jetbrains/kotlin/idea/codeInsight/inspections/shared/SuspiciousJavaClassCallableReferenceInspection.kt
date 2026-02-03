// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.expressionVisitor

internal class SuspiciousJavaClassCallableReferenceInspection :
    KotlinApplicableInspectionBase.Simple<KtCallableReferenceExpression, SuspiciousJavaClassCallableReferenceInspection.Context>() {

    enum class ReceiverKind {
        /**
         * Receiver is an expression.
         *
         * ```
         * fun test(sss: String) {
         *   sss::javaClass
         *   ^^^
         * }
         * ```
         *
         * In this case, `.javaClass` call can be used.
         */
        EXPRESSION,

        /**
         * Receiver is a type reference.
         *
         * ```
         * fun test() {
         *   String::javaClass
         *   ^^^^^^
         * }
         * ```
         *
         * In this case, `.javaClass` call cannot be used,
         * and `::class.java` have to be used instead.
         */
        TYPE,
    }

    class Context(
        val receiverKind: ReceiverKind,
    )

    override fun getProblemDescription(
        element: KtCallableReferenceExpression,
        context: Context
    ): @InspectionMessage String {
        return KotlinBundle.message("inspection.suspicious.javaClass.callable.reference.description")
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = expressionVisitor { expression ->
        if (expression is KtCallableReferenceExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getApplicableRanges(element: KtCallableReferenceExpression): List<TextRange> {
        return ApplicabilityRange.union(element) { listOf(it.doubleColonTokenReference, it.callableReference) }
    }

    override fun isApplicableByPsi(element: KtCallableReferenceExpression): Boolean {
        return element.callableReference.getReferencedNameAsName() == JvmStandardClassIds.Callables.JavaClass.callableName
    }

    override fun KaSession.prepareContext(element: KtCallableReferenceExpression): Context? {
        val resolvedCall = element.resolveToCall()?.singleVariableAccessCall() ?: return null

        if (resolvedCall.symbol.callableId != JvmStandardClassIds.Callables.JavaClass) return null

        val receiverKind = if (resolvedCall.partiallyAppliedSymbol.extensionReceiver != null) {
            // receiver value is present, the receiver is an expression
            ReceiverKind.EXPRESSION
        } else {
            // receiver value is absent, the receiver is a type
            ReceiverKind.TYPE
        }

        return Context(receiverKind)
    }

    override fun createQuickFix(
        element: KtCallableReferenceExpression,
        context: Context
    ): KotlinModCommandQuickFix<KtCallableReferenceExpression> = ReplaceJavaClassCallableReferenceWithProperCallFix(context)

    private class ReplaceJavaClassCallableReferenceWithProperCallFix(
        private val context: Context
    ) : KotlinModCommandQuickFix<KtCallableReferenceExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String {
            val replacement = when (context.receiverKind) {
                ReceiverKind.EXPRESSION -> ".javaClass"
                ReceiverKind.TYPE -> "::class.java"
            }

            return KotlinBundle.message("inspection.suspicious.javaClass.callable.reference.replace.with.0.call.fix", replacement)
        }

        override fun applyFix(
            project: Project,
            element: KtCallableReferenceExpression,
            updater: ModPsiUpdater
        ) {
            val psiFactory = KtPsiFactory(project)

            val explicitReceiver = element.receiverExpression

            val replacementExpression = when (context.receiverKind) {
                ReceiverKind.EXPRESSION -> {
                    if (explicitReceiver != null) {
                        psiFactory.createExpressionByPattern("$0.javaClass", explicitReceiver)
                    } else {
                        psiFactory.createExpression("javaClass")
                    }
                }

                ReceiverKind.TYPE -> {
                    if (explicitReceiver != null) {
                        val receiverExpressionCopy = explicitReceiver.copied()
                        // we need to remove type arguments because `::class` cannot be used on types with generics
                        receiverExpressionCopy.removeTypeArgumentsOnPotentiallyQualifiedCallExpression()

                        psiFactory.createExpressionByPattern("$0::class.java", receiverExpressionCopy)
                    } else {
                        null
                    }
                }
            } ?: return

            element.replace(replacementExpression)
        }
    }
}

private fun KtExpression.removeTypeArgumentsOnPotentiallyQualifiedCallExpression() {
    val qualifiers = splitIntoQualifiersIfQualified()

    for (qualifier in qualifiers) {
        if (qualifier is KtCallElement) {
            qualifier.typeArgumentList?.delete()
        }
    }
}

private fun KtExpression.splitIntoQualifiersIfQualified(): List<KtExpression> {
    val wholeExpression = this

    if (wholeExpression !is KtQualifiedExpression) return listOf(wholeExpression)

    val qualifiedExpressions =
        generateSequence<KtExpression>(wholeExpression) { (it as? KtQualifiedExpression)?.receiverExpression }

    return qualifiedExpressions
        .map { (it as? KtQualifiedExpression)?.selectorExpression ?: it }
        .toList()
}

