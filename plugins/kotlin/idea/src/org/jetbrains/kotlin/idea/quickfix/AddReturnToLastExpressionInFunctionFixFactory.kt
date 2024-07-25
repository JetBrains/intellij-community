// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

internal object AddReturnToLastExpressionInFunctionFixFactory : KotlinSingleIntentionActionFactory() {

    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val casted = Errors.NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY.cast(diagnostic)
        val namedFunction = casted.psiElement as? KtNamedFunction ?: return null
        val last = namedFunction.bodyBlockExpression?.statements?.lastOrNull() ?: return null

        val lastType = last.analyze(BodyResolveMode.PARTIAL).let { context ->
            last.getType(context)?.takeIf { !it.isError }
        } ?: return null

        val expectedType = namedFunction.resolveToDescriptorIfAny()?.returnType?.takeIf { !it.isError } ?: return null
        if (!lastType.isSubtypeOf(expectedType)) return null

        return AddReturnToLastExpressionInFunctionFix(namedFunction).asIntention()
    }
}