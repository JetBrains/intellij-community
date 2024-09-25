// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.builtins.isFunctionOrSuspendFunctionType
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isPrimitiveNumberType
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable

internal object SurroundWithLambdaForTypeMismatchFixFactory : KotlinSingleIntentionActionFactory() {
    private val LOG = Logger.getInstance(SurroundWithLambdaForTypeMismatchFix::class.java)

    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val diagnosticFactory = diagnostic.factory
        val expectedType: KotlinType
        val expressionType: KotlinType
        when (diagnosticFactory) {
            Errors.TYPE_MISMATCH -> {
                val diagnosticWithParameters = Errors.TYPE_MISMATCH.cast(diagnostic)
                expectedType = diagnosticWithParameters.a
                expressionType = diagnosticWithParameters.b
            }

            Errors.TYPE_MISMATCH_WARNING -> {
                val diagnosticWithParameters = Errors.TYPE_MISMATCH_WARNING.cast(diagnostic)
                expectedType = diagnosticWithParameters.a
                expressionType = diagnosticWithParameters.b
            }

            Errors.CONSTANT_EXPECTED_TYPE_MISMATCH -> {
                val context = (diagnostic.psiFile as KtFile).analyzeWithContent()
                val diagnosticWithParameters = Errors.CONSTANT_EXPECTED_TYPE_MISMATCH.cast(diagnostic)

                val diagnosticElement = diagnostic.psiElement
                if (diagnosticElement !is KtExpression) {
                    LOG.error("Unexpected element: " + diagnosticElement.text)
                    return null
                }
                expectedType = diagnosticWithParameters.b
                expressionType = context.getType(diagnosticElement) ?: return null
            }

            else -> {
                LOG.error("Unexpected diagnostic: " + DefaultErrorMessages.render(diagnostic))
                return null
            }
        }

        if (!expectedType.isFunctionOrSuspendFunctionType) return null
        if (expectedType.arguments.size != 1) return null
        val lambdaReturnType = expectedType.arguments[0].type

        if (!expressionType.makeNotNullable().isSubtypeOf(lambdaReturnType) &&
            !(expressionType.isPrimitiveNumberType() && lambdaReturnType.isPrimitiveNumberType())
        ) return null

        val diagnosticElement = diagnostic.psiElement as KtExpression
        return SurroundWithLambdaForTypeMismatchFix(diagnosticElement).asIntention()
    }
}