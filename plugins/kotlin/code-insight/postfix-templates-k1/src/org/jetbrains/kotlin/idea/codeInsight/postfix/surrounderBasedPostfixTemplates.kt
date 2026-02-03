// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression.KotlinWhenSurrounder
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression.KotlinWithIfExpressionSurrounder
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement.KotlinTryCatchSurrounder

internal class KtNotNullPostfixTemplate(val name: String, provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    name, "if (expr != null)",
    KtPostfixTemplatePsiInfo, createNullableExpressionSelector(), provider
) {
    override fun getSurrounder(): KotlinWithIfExpressionSurrounder = KotlinWithIfExpressionSurrounder(withElse = false)
    override fun getTail(): String = "!= null"
}

internal class KtIsNullPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "null", "if (expr == null)",
    KtPostfixTemplatePsiInfo, createNullableExpressionSelector(), provider
) {
    override fun getSurrounder(): KotlinWithIfExpressionSurrounder = KotlinWithIfExpressionSurrounder(withElse = false)
    override fun getTail(): String = "== null"
}

internal class KtWhenExpressionPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "when", "when (expr)",
    KtPostfixTemplatePsiInfo, createPostfixExpressionSelector(), provider
) {
    override fun getSurrounder(): KotlinWhenSurrounder = KotlinWhenSurrounder()
}

internal class KtTryPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "try", "try { code } catch (e: Exception) { }",
    KtPostfixTemplatePsiInfo,
    createPostfixExpressionSelector(
        checkCanBeUsedAsValue = false,
        // Do not suggest 'val x = try { init } catch (e: Exception) { }'
        statementsOnly = true
    ),
    provider
) {
    override fun getSurrounder(): KotlinTryCatchSurrounder = KotlinTryCatchSurrounder()
}
