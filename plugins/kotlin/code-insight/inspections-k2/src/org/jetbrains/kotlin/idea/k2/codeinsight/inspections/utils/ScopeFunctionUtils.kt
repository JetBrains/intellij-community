// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.idea.k2.refactoring.getThisReceiverOwner
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

internal val counterpartNames: Map<String, Any> = mapOf(
    "apply" to "also",
    "run" to listOf("with", "let"),
    "also" to "apply",
    "let" to listOf("run", "with"),
    "with" to listOf("run", "let"),
)

// Scope functions that use an implicit 'this' receiver
internal val implicitThisFunctions: Set<String> = setOf("with", "run", "apply")

// Scope functions that use explicit parameter (like 'it')
internal val explicitParameterFunctions: Set<String> = setOf("let", "also")

internal fun usesImplicitThis(functionName: String): Boolean = functionName in implicitThisFunctions
internal fun usesExplicitParameter(functionName: String): Boolean = functionName in explicitParameterFunctions

/**
 * Checks if a lambda with parameters can be converted to a different scope function.
 * Only simple cases are supported - no destructuring, no nested lambdas, no string templates.
 *
 * @param lambdaExpression The lambda expression to check
 * @return True if this is a simple case that can be converted
 */
internal fun isSimpleLambdaParameterCase(lambdaExpression: KtLambdaExpression): Boolean {
    val parameters = lambdaExpression.valueParameters
    
    // Must have exactly one parameter
    if (parameters.size != 1) return false
    
    // Must not be destructuring
    val parameter = parameters.first()
    if (parameter.destructuringDeclaration != null) return false
    
    // Check the lambda body for unsupported constructs
    val bodyExpression = lambdaExpression.bodyExpression ?: return false
    
    var isSimple = true
    bodyExpression.accept(object : KtTreeVisitorVoid() {
        override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
            super.visitLambdaExpression(lambdaExpression)
            // No nested lambdas for now
            isSimple = false
        }
        
        override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
            super.visitStringTemplateExpression(expression)
            // Check if parameter is used in string template
            expression.entries.forEach { entry ->
                if (entry is KtStringTemplateEntryWithExpression) {
                    val expr = entry.expression
                    if (expr is KtDotQualifiedExpression) {
                        val receiver = expr.receiverExpression
                        if (receiver is KtSimpleNameExpression && receiver.mainReference.resolve() == parameter) {
                            isSimple = false
                        }
                    }
                }
            }
        }
    })
    
    return isSimple
}

/**
 * Checks if a receiver value is from the function literal we're converting.
 * This helps identify which property/method calls should be transformed.
 */
internal fun KaSession.isReceiverFromFunctionLiteral(receiver: KaReceiverValue?, functionLiteral: KtFunctionLiteral?): Boolean {
    if (receiver is KaExplicitReceiverValue) return receiver.expression.mainReference?.resolve() == functionLiteral
    if (receiver is KaImplicitReceiverValue) return receiver.getThisReceiverOwner()?.psi == functionLiteral
    return false
}
