// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.contentRange
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class ConvertTryFinallyToUseCallInspection :
    KotlinApplicableInspectionBase.Simple<KtTryExpression, ConvertTryFinallyToUseCallInspection.Context>() {

    data class Context(val qualifiedFinallyCall: KtCallExpression)

    override fun getProblemDescription(element: KtTryExpression, context: Context): String =
        KotlinBundle.message("convert.try.finally.to.use.before.text")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitTryExpression(expression: KtTryExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getApplicableRanges(element: KtTryExpression): List<TextRange> {
        val range = TextRange(
            element.startOffset,
            element.tryBlock.lBrace?.endOffset ?: element.endOffset
        ).shiftLeft(element.startOffset)
        return listOf(range)
    }

    override fun isApplicableByPsi(element: KtTryExpression): Boolean {
        if (element.catchClauses.isNotEmpty()) return false

        val finallySection = element.finallyBlock ?: return false
        val stmt = finallySection.finalExpression.statements.singleOrNull() ?: return false
        val call = (stmt as? KtQualifiedExpression)?.selectorExpression as? KtCallExpression ?: (stmt as? KtCallExpression) ?: return false
        val calleeText = call.calleeExpression?.text
        if (calleeText != "close") return false
        return true
    }

    override fun KaSession.prepareContext(element: KtTryExpression): Context? {
        val finallySection = element.finallyBlock ?: return null
        val stmt = finallySection.finalExpression.statements.singleOrNull() ?: return null
        val call = (stmt as? KtQualifiedExpression)?.selectorExpression as? KtCallExpression ?: (stmt as? KtCallExpression) ?: return null

        // Must be a member call with no value args
        if (call.valueArguments.isNotEmpty()) return null
        val resolved = call.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>() ?: return null

        // Check that it's Closeable.close or AutoCloseable.close
        val partiallyAppliedSymbol = resolved.partiallyAppliedSymbol
        val callableSymbol = partiallyAppliedSymbol.symbol
        if (callableSymbol.name?.asString() != "close") return null
        val classSymbol = callableSymbol.containingSymbol as? KaClassSymbol ?: return null
        val closeableClass = findClass(ClassId.fromString("java.io/Closeable")) ?: return null
        val autoCloseableClass = findClass(ClassId.fromString("java.lang/AutoCloseable"))

        if (classSymbol != closeableClass && classSymbol != autoCloseableClass &&
            !classSymbol.isSubClassOf(closeableClass) && (autoCloseableClass == null || !classSymbol.isSubClassOf(autoCloseableClass))) {
            return null
        }

        if (callableSymbol.allOverriddenSymbols.none { it.containingSymbol in setOfNotNull(closeableClass, autoCloseableClass) }) {
            return null
        }

        // Receiver must be either implicit this or an expression that is a name reference (val)
        val receiverExpression = (stmt as? KtQualifiedExpression)?.receiverExpression
        if (receiverExpression != null) {
            if (receiverExpression !is KtThisExpression) {
                // For safety, only allow simple name receivers; other complex expressions are not supported
                val nameRef = receiverExpression as? KtNameReferenceExpression ?: return null
                // We don't enforce val/var here due to API/module constraints; K1 version disallows var
            }
        } else {
            // No explicit receiver -> must be implicit receiver on super/this, allow
        }

        return Context(call)
    }

    override fun createQuickFix(
        element: KtTryExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtTryExpression> = object : KotlinModCommandQuickFix<KtTryExpression>() {
        override fun getFamilyName(): String = KotlinBundle.message("convert.try.finally.to.use")

        override fun applyFix(project: Project, element: KtTryExpression, updater: ModPsiUpdater) {
            val finallySection = element.finallyBlock ?: return
            val finallyExpression = finallySection.finalExpression.statements.singleOrNull() ?: return
            val finallyExpressionReceiver = (finallyExpression as? KtQualifiedExpression)?.receiverExpression
            val resourceReference = finallyExpressionReceiver as? KtNameReferenceExpression
            val resourceName = resourceReference?.getReferencedNameAsName()

            val psiFactory = KtPsiFactory(element.project)
            val useCallExpression = psiFactory.buildExpression {
                if (resourceName != null) {
                    appendName(resourceName)
                    appendFixedText(".")
                } else if (finallyExpressionReceiver is KtThisExpression) {
                    appendFixedText(finallyExpressionReceiver.text)
                    appendFixedText(".")
                }
                appendFixedText("use {")
                if (resourceName != null) {
                    appendName(resourceName)
                    appendFixedText("->")
                }
                appendFixedText("\n")
                appendChildRange(element.tryBlock.contentRange())
                appendFixedText("\n}")
            }

            val call = when (val result = element.replace(useCallExpression) as KtExpression) {
                is KtQualifiedExpression -> result.selectorExpression as? KtCallExpression ?: return
                is KtCallExpression -> result
                else -> return
            }
            val lambda = call.lambdaArguments.firstOrNull() ?: return
            val lambdaParameter = lambda.getLambdaExpression()?.valueParameters?.firstOrNull() ?: return
            updater.select(lambdaParameter)
        }
    }
}
