// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.unwrapParenthesesLabelsAndAnnotations
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object AddExclExclCallFixFactories {

    val unsafeCallFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UnsafeCall ->
        getFixForUnsafeCall(diagnostic.psi)
    }

    val unsafeInfixCallFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UnsafeInfixCall ->
        getFixForUnsafeCall(diagnostic.psi)
    }

    val unsafeOperatorCallFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UnsafeOperatorCall ->
        getFixForUnsafeCall(diagnostic.psi)
    }

    private fun KaSession.getFixForUnsafeCall(psi: PsiElement): List<AddExclExclCallFix> {
        val (target, hasImplicitReceiver) = when (val unwrapped = psi.unwrapParenthesesLabelsAndAnnotations()) {
            // `foo.bar` -> `foo!!.bar`
            is KtDotQualifiedExpression -> unwrapped.receiverExpression to false

            // `foo[bar]` -> `foo!![bar]`
            is KtArrayAccessExpression -> unwrapped.arrayExpression to false

            is KtCallableReferenceExpression -> unwrapped.lhs.let { lhs ->
                if (lhs != null) {
                    // `foo::bar` -> `foo!!::bar`
                    lhs to false
                } else {
                    // `::bar -> this!!::bar`
                    unwrapped to true
                }
            }

            // `bar` -> `this!!.bar`
            is KtNameReferenceExpression -> unwrapped to true

            // `bar()` -> `this!!.bar()`
            is KtCallExpression -> unwrapped to true

            // `-foo` -> `-foo!!`
            // NOTE: Unsafe unary operator call is reported as UNSAFE_CALL, _not_ UNSAFE_OPERATOR_CALL
            is KtUnaryExpression -> unwrapped.baseExpression to false

            is KtBinaryExpression -> {
                val receiver = when {
                    KtPsiUtil.isInOrNotInOperation(unwrapped) ->
                        // `bar in foo` -> `bar in foo!!`
                        unwrapped.right
                    KtPsiUtil.isAssignment(unwrapped) ->
                        // UNSAFE_CALL for assignments (e.g., `foo.bar = value`) is reported on the entire statement (KtBinaryExpression).
                        // The unsafe call is on the LHS of the assignment.
                        return getFixForUnsafeCall(unwrapped.left ?: return emptyList())
                    else ->
                        // `foo + bar` -> `foo!! + bar` OR `foo infixFun bar` -> `foo!! infixFun bar`
                        unwrapped.left
                }
                receiver to false
            }

            // UNSAFE_INFIX_CALL/UNSAFE_OPERATOR_CALL on KtBinaryExpression is reported on the child KtOperationReferenceExpression
            is KtOperationReferenceExpression -> return getFixForUnsafeCall(unwrapped.parent)

            else -> return emptyList()
        }

        // We don't want to offer AddExclExclCallFix if we know the expression is definitely null, e.g.:
        //
        //   if (nullableInt == null) {
        //     val x = nullableInt.length  // No AddExclExclCallFix here
        //   }
        if (target?.safeAs<KtExpression>()?.isDefinitelyNull == true) {
            return emptyList()
        }

        return listOfNotNull(target.asAddExclExclCallFix(hasImplicitReceiver = hasImplicitReceiver))
    }

    @OptIn(KaExperimentalApi::class)
    val iteratorOnNullableFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.IteratorOnNullable ->
        val expression = diagnostic.psi as? KtExpression
            ?: return@IntentionBased emptyList()
        val type = expression.expressionType
            ?: return@IntentionBased emptyList()
        if (!type.canBeNull)
            return@IntentionBased emptyList()

        // NOTE: This is different from FE1.0 in that we offer the fix even if the function does NOT have the `operator` modifier.
        // Adding `!!` will then surface the error that `operator` should be added (with corresponding fix).
        val typeScope = type.scope?.declarationScope
            ?: return@IntentionBased emptyList()
        val hasValidIterator = typeScope.callables(OperatorNameConventions.ITERATOR)
            .filter { it is KaNamedFunctionSymbol && it.valueParameters.isEmpty() }.singleOrNull() != null
        if (hasValidIterator) {
            listOfNotNull(expression.asAddExclExclCallFix())
        } else {
            emptyList()
        }
    }
}

internal fun PsiElement?.asAddExclExclCallFix(hasImplicitReceiver: Boolean = false) =
    this?.let { AddExclExclCallFix(it, hasImplicitReceiver) }
