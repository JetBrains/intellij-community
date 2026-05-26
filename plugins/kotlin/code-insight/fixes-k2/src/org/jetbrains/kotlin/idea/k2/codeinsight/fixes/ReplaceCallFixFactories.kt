// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.isSubClassOf
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.findClass
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getImplicitReceivers
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.RemoveRedundantCallsOfConversionMethodsFix
import org.jetbrains.kotlin.idea.quickfix.ReplaceImplicitReceiverCallFix
import org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix
import org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallFix
import org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallForScopeFunctionFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.unwrapParenthesesLabelsAndAnnotations
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object ReplaceCallFixFactories {
    val redundantCallsOfConversionMethods: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.RedundantCallOfConversionMethod> =
    KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.RedundantCallOfConversionMethod ->
        val element = diagnostic.psi as? KtQualifiedExpression ?: return@ModCommandBased emptyList()
        listOf(RemoveRedundantCallsOfConversionMethodsFix(element))
    }

    val unsafeCallFactory: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.UnsafeCall> =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnsafeCall ->
            val psi = diagnostic.psi
            val target = if (psi is KtBinaryExpression && psi.operationToken in KtTokens.ALL_ASSIGNMENTS) {
                // UNSAFE_CALL for assignments (e.g., `foo.bar = value`) is reported on the entire statement (KtBinaryExpression).
                // The unsafe call is on the LHS of the assignment.
                psi.left
            } else {
                psi
            }.unwrapParenthesesLabelsAndAnnotations()

            val shouldHaveNotNullType = target.safeAs<KtExpression>()?.let { shouldHaveNotNullType(it) } ?: false
            val safeFix = when (target) {
                is KtDotQualifiedExpression -> ReplaceWithSafeCallFix(target, shouldHaveNotNullType)
                is KtNameReferenceExpression, is KtCallExpression -> {
                    // TODO: As a safety precaution, resolve the expression to determine if it is a call with an implicit receiver.
                    // This is a defensive check to ensure that the diagnostic was reported on such a call and not some other name reference.
                    // This isn't strictly needed because FIR checkers aren't reporting on wrong elements, but ReplaceWithSafeCallFixFactory
                    // in FE1.0 does so.
                    ReplaceImplicitReceiverCallFix(target, shouldHaveNotNullType)
                }

                is KtArrayAccessExpression -> ReplaceInfixOrOperatorCallFix(target, shouldHaveNotNullType)
                else -> null
            }
            listOfNotNull(safeFix, createReplaceWithSafeCallForScopeFunctionFix(psi))
        }

    val unsafeInfixCallFactory: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.UnsafeInfixCall> =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnsafeInfixCall ->
            val psi = diagnostic.psi
            val target = psi.parent as? KtBinaryExpression
                ?: return@ModCommandBased emptyList()
            listOf(ReplaceInfixOrOperatorCallFix(target, shouldHaveNotNullType(target), diagnostic.operator))
        }

    val unsafeOperatorCallFactory: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.UnsafeOperatorCall> =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnsafeOperatorCall ->
            val psi = diagnostic.psi
            val operationToken = when (psi) {
                is KtOperationReferenceExpression -> psi.getReferencedNameElementType()
                is KtBinaryExpression -> psi.operationToken
                else -> null
            }
            if (operationToken == KtTokens.EQ || operationToken in OperatorConventions.COMPARISON_OPERATIONS) {
                // This matches FE1.0 behavior; see ReplaceInfixOrOperatorCallFixFactory.kt
                return@ModCommandBased emptyList()
            }
            val target = psi.getNonStrictParentOfType<KtBinaryExpression>()
                ?: return@ModCommandBased emptyList()
            val left = target.left
            if (operationToken in KtTokens.AUGMENTED_ASSIGNMENTS && left is KtArrayAccessExpression) {
                val type = left.arrayExpression?.expressionType
                    ?: return@ModCommandBased emptyList()
                val argumentType = (type as? KaClassType)?.typeArguments?.firstOrNull()
                if (type.isMap() && argumentType?.type?.isMarkedNullable != true) {
                    return@ModCommandBased emptyList()
                }
            }
            listOf(ReplaceInfixOrOperatorCallFix(target, shouldHaveNotNullType(target), diagnostic.operator))
        }

    val unsafeImplicitInvokeCallFactory: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.UnsafeImplicitInvokeCall> =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnsafeImplicitInvokeCall ->
            val target = diagnostic.psi as? KtNameReferenceExpression
                ?: return@ModCommandBased emptyList()

            val callExpression = target.parent as? KtCallExpression
                ?: return@ModCommandBased emptyList()
            val qualifiedExpression = callExpression.parent as? KtQualifiedExpression
            if (qualifiedExpression == null) {
                // TODO: This matches FE 1.0 behavior (see ReplaceInfixOrOperatorCallFixFactory.kt) but we should be able to do the fix
                // when the call is a qualified expression. We just need to make sure to pass any extension receiver as an argument, e.g.:
                //
                //   fun test(exec: (String.() -> Unit)?) = "".exec()  // Can be fixed to exec?.invoke("")
                //
                // This should be differentiated from this case without an extension receiver:
                //
                //   class A(val exec: (() -> Unit)?)
                //   fun test(a: A) = a.exec()  // Can be fixed to a.exec?.invoke()
                listOf(ReplaceInfixOrOperatorCallFix(callExpression, shouldHaveNotNullType(callExpression)))
            } else emptyList()
        }

    private fun KaSession.shouldHaveNotNullType(expression: KtExpression): Boolean {
        // This function is used to determine if we may need to add an elvis operator after the safe call. For example, to replace
        // `s.length` in `val x: Int = s.length` with a safe call, it should be replaced with `s.length ?: <caret>`.
        val expectedType = expression.expectedType ?: return false
        return !expectedType.isMarkedNullable && !expectedType.isUnitType
    }

    context(_: KaSession)
    private fun KaType.isMap(): Boolean {
        val symbol = this.expandedSymbol ?: return false
        if (symbol.name?.asString()?.endsWith("Map") != true) return false
        val mapSymbol = findClass(StandardClassIds.Map) ?: return false
        return symbol.isSubClassOf(mapSymbol)
    }

    context(session: KaSession)
    private fun createReplaceWithSafeCallForScopeFunctionFix(psi: PsiElement): ReplaceWithSafeCallForScopeFunctionFix? {
        val scopeFunctionLiteral = psi.getStrictParentOfType<KtFunctionLiteral>() ?: return null
        val scopeCallExpression = scopeFunctionLiteral.getStrictParentOfType<KtCallExpression>() ?: return null
        val scopeDotQualifiedExpression = scopeCallExpression.getStrictParentOfType<KtDotQualifiedExpression>() ?: return null

        val scopeFunctionSymbol = scopeFunctionLiteral.symbol

        val scopeFunctionKind = scopeCallExpression.scopeFunctionKind() ?: return null

        val internalReceiver = (psi as? KtDotQualifiedExpression)?.receiverExpression
        val internalReceiverSymbol = internalReceiver?.resolveToCall()?.singleVariableAccessCall()?.symbol
        val internalResolvedCall = (psi.getParentOfType<KtElement>(strict = false))?.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()
            ?: return null

        when (scopeFunctionKind) {
            ScopeFunctionKind.WITH_PARAMETER -> {
                if (internalReceiverSymbol != scopeFunctionSymbol.valueParameters.singleOrNull()) {
                    return null
                }
            }
            ScopeFunctionKind.WITH_RECEIVER -> {
                if (!(internalReceiverSymbol == scopeFunctionSymbol.receiverParameter ||
                            (internalReceiver as? KtThisExpression)?.instanceReference?.mainReference?.resolve() == scopeFunctionLiteral ||
                            internalReceiverSymbol == null && internalResolvedCall.getImplicitReceivers().any { it.symbol == scopeFunctionSymbol.receiverParameter })
                ) {
                    return null
                }
            }
        }

        return ReplaceWithSafeCallForScopeFunctionFix(scopeDotQualifiedExpression, session.shouldHaveNotNullType(scopeCallExpression))
    }

    context(_: KaSession)
    private fun KtCallExpression.scopeFunctionKind(): ScopeFunctionKind? {
        val methodName = resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId?.asSingleFqName()
        return ScopeFunctionKind.entries.firstOrNull { kind -> kind.names.contains(methodName?.asString()) }
    }

    private enum class ScopeFunctionKind(vararg val names: String) {
        WITH_PARAMETER("kotlin.let", "kotlin.also"),
        WITH_RECEIVER("kotlin.apply", "kotlin.run")
    }
}
