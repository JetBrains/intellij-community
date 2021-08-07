// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression.KotlinWhenSurrounder
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression.KotlinWithIfExpressionSurrounder
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement.KotlinTryCatchSurrounder
import org.jetbrains.kotlin.idea.intentions.negate
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.isBoolean


internal object KtIfExpressionPostfixTemplate : SurroundPostfixTemplateBase(
    "if", "if (expr)",
    KtPostfixTemplatePsiInfo, createExpressionSelector { it.isBoolean() }
) {
    override fun getSurrounder() = KotlinWithIfExpressionSurrounder(withElse = false)
}

internal object KtElseExpressionPostfixTemplate : SurroundPostfixTemplateBase(
    "else", "if (!expr)",
    KtPostfixTemplatePsiInfo, createExpressionSelector { it.isBoolean() }
) {
    override fun getSurrounder() = KotlinWithIfExpressionSurrounder(withElse = false)
    override fun getWrappedExpression(expression: PsiElement?) = (expression as KtExpression).negate()
}

internal class KtNotNullPostfixTemplate(val name: String) : SurroundPostfixTemplateBase(
    name, "if (expr != null)",
    KtPostfixTemplatePsiInfo, createExpressionSelector(typePredicate = TypeUtils::isNullableType)
) {
    override fun getSurrounder() = KotlinWithIfExpressionSurrounder(withElse = false)
    override fun getTail() = "!= null"
}

internal object KtIsNullPostfixTemplate : SurroundPostfixTemplateBase(
    "null", "if (expr == null)",
    KtPostfixTemplatePsiInfo, createExpressionSelector(typePredicate = TypeUtils::isNullableType)
) {
    override fun getSurrounder() = KotlinWithIfExpressionSurrounder(withElse = false)
    override fun getTail() = "== null"
}

internal object KtWhenExpressionPostfixTemplate : SurroundPostfixTemplateBase(
    "when", "when (expr)",
    KtPostfixTemplatePsiInfo, createExpressionSelector()
) {
    override fun getSurrounder() = KotlinWhenSurrounder()
}

internal object KtTryPostfixTemplate : SurroundPostfixTemplateBase(
    "try", "try { code } catch (e: Exception) { }",
    KtPostfixTemplatePsiInfo,
    createExpressionSelector(
        checkCanBeUsedAsValue = false,
        // Do not suggest 'val x = try { init } catch (e: Exception) { }'
        statementsOnly = true
    )
) {
    override fun getSurrounder() = KotlinTryCatchSurrounder()
}
