// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Checks if nested lambdas would conflict with implicit 'it' parameter.
 * This is used to determine if a unique parameter name is needed when converting scope functions.
 *
 * @param lambdaArgument The lambda argument to check
 * @return True if nested lambdas would conflict with implicit 'it'
 */
internal fun KaSession.hasImplicitItConflicts(lambdaArgument: KtLambdaArgument): Boolean {
    // Check if 'it' is already used in the current scope
    val nameValidator = KotlinDeclarationNameValidator(
        visibleDeclarationsContext = lambdaArgument,
        checkVisibleDeclarationsContext = true,
        target = KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
    )

    // If 'it' is already invalid in the current scope, we need a unique name
    var needUniqueName = !nameValidator.validate(StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier)

    // If 'it' is valid in the current scope, check nested scopes for conflicts
    if (!needUniqueName) {
        val outerLambda = lambdaArgument.getLambdaExpression()
        
        lambdaArgument.accept(object : KtTreeVisitorVoid() {
            override fun visitDeclaration(dcl: KtDeclaration) {
                super.visitDeclaration(dcl)
                checkNeedUniqueName(dcl)
            }

            override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                // Only check nested lambdas, not the outer lambda being converted
                if (lambdaExpression != outerLambda) {
                    val lambdaType = lambdaExpression.expressionType as? KaFunctionType
                    val hasReceiver = lambdaType?.receiverType != null
                    val declaredParams = lambdaExpression.valueParameters
                    
                    // Check if a nested lambda would conflict with implicit 'it'
                    // If a nested lambda uses implicit 'it', it would conflict with outer 'it'
                    if (declaredParams.isEmpty() && lambdaType?.isFunctionType == true && !hasReceiver) {
                        needUniqueName = true
                    }
                    
                    // If a nested lambda declares 'it' explicitly, it would conflict
                    if (declaredParams.any { it.name == StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier }) {
                        needUniqueName = true
                    }
                }
                super.visitLambdaExpression(lambdaExpression)
            }

            private fun checkNeedUniqueName(element: KtElement) {
                // Check if 'it' is valid in this nested scope
                val nestedValidator = KotlinDeclarationNameValidator(
                    visibleDeclarationsContext = element,
                    checkVisibleDeclarationsContext = true,
                    target = KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
                )

                if (!nestedValidator.validate(StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier)) {
                    needUniqueName = true
                }
            }
        })
    }
    return needUniqueName
}

/**
 * Finds a unique parameter name for the lambda.
 * Tries to suggest a name based on the type of the receiver, or falls back to a generic name.
 *
 * @param lambdaArgument The lambda argument to find a name for
 * @return A unique parameter name
 */
internal fun KaSession.findUniqueParameterName(lambdaArgument: KtLambdaArgument): String {
    // Create a name validator to check if suggested names are valid
    val nameValidator = KotlinDeclarationNameValidator(
        visibleDeclarationsContext = lambdaArgument,
        checkVisibleDeclarationsContext = true,
        target = KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
    )

    // Get the receiver type from the call expression
    val callExpression = lambdaArgument.getStrictParentOfType<KtCallExpression>()
    val resolvedCall = callExpression?.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()
    val dispatchReceiver = resolvedCall?.partiallyAppliedSymbol?.dispatchReceiver
    val extensionReceiver = resolvedCall?.partiallyAppliedSymbol?.extensionReceiver
    val parameterType = dispatchReceiver?.type ?: extensionReceiver?.type

    // If we have a type, suggest a name based on it
    return if (parameterType != null) {
        with(KotlinNameSuggester()) {
            suggestTypeNames(parameterType).map { typeName ->
                KotlinNameSuggester.suggestNameByName(typeName) { nameValidator.validate(it) }
            }
        }.first()
    } else {
        // Otherwise, use a generic name like "p1", "p2", etc.
        KotlinNameSuggester.suggestNameByName("p") { candidate ->
            nameValidator.validate(candidate)
        }
    }
}