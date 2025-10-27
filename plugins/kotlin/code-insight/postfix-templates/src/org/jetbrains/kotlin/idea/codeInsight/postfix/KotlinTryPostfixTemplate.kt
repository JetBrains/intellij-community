// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement.KotlinTryCatchSurrounder

internal class KotlinTryPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase (
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
