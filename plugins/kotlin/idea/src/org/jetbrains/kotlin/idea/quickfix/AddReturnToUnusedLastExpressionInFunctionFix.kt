// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

internal object AddReturnToUnusedLastExpressionInFunctionFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val casted = Errors.UNUSED_EXPRESSION.cast(diagnostic)
        val expression = casted.psiElement as? KtExpression ?: return null
        if (!expression.isLastStatementInFunctionBody()) return null

        val expressionType = expression.analyze(BodyResolveMode.PARTIAL).let { context ->
            expression.getType(context)?.takeIf { !it.isError }
        } ?: return null

        val function = expression.parent?.parent as? KtNamedFunction
        val functionReturnType = function?.resolveToDescriptorIfAny()?.returnType?.takeIf { !it.isError } ?: return null

        if (!expressionType.isSubtypeOf(functionReturnType)) return null
        return AddReturnToUnusedLastExpressionInFunctionFix(expression)
    }
}

private fun KtExpression.isLastStatementInFunctionBody(): Boolean {
    val body = this.parent as? KtBlockExpression ?: return false
    val last = body.statements.lastOrNull() ?: return false
    return last === this
}