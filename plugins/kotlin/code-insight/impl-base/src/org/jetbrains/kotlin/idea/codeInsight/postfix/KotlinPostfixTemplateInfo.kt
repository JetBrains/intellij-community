// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

object KotlinPostfixTemplateInfo {
    /**
     * In tests only one expression should be suggested, so in case there are many of them, save relevant items.
     */
    var PsiFile.suggestedExpressions: List<String> by NotNullableUserDataProperty(
        Key("KOTLIN_POSTFIX_TEMPLATE_EXPRESSIONS"),
        defaultValue = emptyList(),
    )
}