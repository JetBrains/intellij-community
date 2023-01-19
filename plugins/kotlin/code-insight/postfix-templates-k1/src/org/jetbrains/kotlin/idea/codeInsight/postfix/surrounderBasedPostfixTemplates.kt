// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression.KotlinWhenSurrounder
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression.KotlinWithIfExpressionSurrounder
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement.KotlinTryCatchSurrounder
import org.jetbrains.kotlin.idea.intentions.negate
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.isBoolean


internal class KtIfExpressionPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "if", "if (expr)",
    KtPostfixTemplatePsiInfo, createExpressionSelector { it.isBoolean() }, provider
) {
    override fun getSurrounder() = KotlinWithIfExpressionSurrounder(withElse = false)
}

internal class KtElseExpressionPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "else", "if (!expr)",
    KtPostfixTemplatePsiInfo, createExpressionSelector { it.isBoolean() }, provider
) {
    override fun getSurrounder() = KotlinWithIfExpressionSurrounder(withElse = false)
    override fun getWrappedExpression(expression: PsiElement?) = (expression as KtExpression).negate()
}

internal class KtNotNullPostfixTemplate(val name: String, provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    name, "if (expr != null)",
    KtPostfixTemplatePsiInfo, createExpressionSelector(typePredicate = TypeUtils::isNullableType), provider
) {
    override fun getSurrounder() = KotlinWithIfExpressionSurrounder(withElse = false)
    override fun getTail() = "!= null"
}

internal class KtIsNullPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "null", "if (expr == null)",
    KtPostfixTemplatePsiInfo, createExpressionSelector(typePredicate = TypeUtils::isNullableType), provider
) {
    override fun getSurrounder() = KotlinWithIfExpressionSurrounder(withElse = false)
    override fun getTail() = "== null"
}

internal class KtWhenExpressionPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "when", "when (expr)",
    KtPostfixTemplatePsiInfo, createExpressionSelector(), provider
) {
    override fun getSurrounder() = KotlinWhenSurrounder()
}

internal class KtTryPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "try", "try { code } catch (e: Exception) { }",
    KtPostfixTemplatePsiInfo,
    createExpressionSelector(
        checkCanBeUsedAsValue = false,
        // Do not suggest 'val x = try { init } catch (e: Exception) { }'
        statementsOnly = true
    ),
    provider
) {
    override fun getSurrounder() = KotlinTryCatchSurrounder()
}
