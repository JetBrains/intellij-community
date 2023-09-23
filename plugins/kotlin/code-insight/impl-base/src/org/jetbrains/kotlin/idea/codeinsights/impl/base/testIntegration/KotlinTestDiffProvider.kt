// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.testIntegration

import com.intellij.execution.testframework.JvmTestDiffProvider
import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.isInjectionHost

class KotlinTestDiffProvider : JvmTestDiffProvider() {
    override fun getExpectedElement(expression: UExpression, expected: String): PsiElement? {
        if (expression.isInjectionHost() && expression.asSafely<ULiteralExpression>()?.evaluateString()?.withoutLineEndings() == expected) {
            return expression.sourcePsi?.parent // dont get template
        }
        return null
    }
}