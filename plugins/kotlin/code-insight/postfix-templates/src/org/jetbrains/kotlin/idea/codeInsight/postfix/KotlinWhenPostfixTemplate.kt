// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression.KotlinWhenSurrounder

internal class KotlinWhenPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "when", "when (expr)",
    KtPostfixTemplatePsiInfo, createPostfixExpressionSelector(), provider
) {
    override fun getSurrounder(): KotlinWhenSurrounder = KotlinWhenSurrounder()
}