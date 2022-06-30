// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.quickfix.EqualityNotApplicableFactory.isNumberType
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.types.typeUtil.isChar

object IncompatibleTypesFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val diagnosticWithParameters = Errors.INCOMPATIBLE_TYPES.cast(diagnostic)
        val element = diagnostic.psiElement
        val actualType = diagnosticWithParameters.a
        val expectedType = diagnosticWithParameters.b
        return buildList {
            when {
                element is KtExpression && actualType.isNumberType() && expectedType.isNumberType() ->
                    add(NumberConversionFix(element, actualType, expectedType, enableNullableType = true))
                element is KtStringTemplateExpression && expectedType.isChar() ->
                    add(ConvertStringToCharLiteralFix(element))
            }
        }
    }
}
