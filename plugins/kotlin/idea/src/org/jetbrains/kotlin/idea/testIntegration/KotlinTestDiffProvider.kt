// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration

import com.intellij.execution.testframework.actions.TestDiffProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.siyeh.ig.testFrameworks.UAssertHint
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.toUElementOfType

class KotlinTestDiffProvider : TestDiffProvider {
    override fun getInjectionLiteral(project: Project, stackTrace: String): PsiElement? {
        return null
    }

    private fun getExpectedLiteral(callExpression: KtCallExpression): KtStringTemplateEntry? {
        val uCallExpression = callExpression.toUElementOfType<UCallExpression>() ?: return null
        val hint = UAssertHint.createAssertEqualsUHint(uCallExpression) ?: return null
        val expression = hint.expected.sourcePsi
        if (expression is KtStringTemplateEntry) return expression
        return null
    }
}