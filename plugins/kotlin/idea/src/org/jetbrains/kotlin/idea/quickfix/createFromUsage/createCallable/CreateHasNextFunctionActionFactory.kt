// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable

import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.project.builtIns
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.CallableInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.FunctionInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.TypeInfo
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.util.OperatorNameConventions

object CreateHasNextFunctionActionFactory : CreateCallableMemberFromUsageFactory<KtForExpression>() {
    override fun getElementOfInterest(diagnostic: Diagnostic): KtForExpression? {
        return diagnostic.psiElement.findParentOfType(strict = false)
    }

    override fun createCallableInfo(element: KtForExpression, diagnostic: Diagnostic): CallableInfo {
        val diagnosticWithParameters =
            DiagnosticFactory.cast(diagnostic, Errors.HAS_NEXT_MISSING, Errors.HAS_NEXT_FUNCTION_NONE_APPLICABLE)
        val ownerType = TypeInfo(diagnosticWithParameters.a, Variance.IN_VARIANCE)
        val returnType = TypeInfo(element.builtIns.booleanType, Variance.OUT_VARIANCE)
        return FunctionInfo(
            OperatorNameConventions.HAS_NEXT.asString(),
            ownerType,
            returnType,
            modifierList = KtPsiFactory(element.project).createModifierList(KtTokens.OPERATOR_KEYWORD)
        )
    }
}
