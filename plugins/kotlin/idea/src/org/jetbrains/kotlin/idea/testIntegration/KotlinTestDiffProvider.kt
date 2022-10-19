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
    //override fun getExpectedElement(
    //    file: PsiFile,
    //    range: TextRange
    //): PsiElement? {
    //    val failedCallExpression = (findElementsOfClassInRange(
    //        file, range.startOffset, range.endOffset, KtCallExpression::class.java
    //    ).firstOrNull() as? KtCallExpression) ?: return null
    //    return getExpectedLiteral(failedCallExpression)
    //}

    override fun getInjectionLiteral(project: Project, stackTrace: String): PsiElement {
        TODO("Not yet implemented")
    }

    private fun getExpectedLiteral(callExpression: KtCallExpression): KtStringTemplateEntry? {
        val uCallExpression = callExpression.toUElementOfType<UCallExpression>() ?: return null
        val hint = UAssertHint.createAssertEqualsUHint(uCallExpression) ?: return null
        val expression = hint.expected.sourcePsi
        if (expression is KtStringTemplateEntry) return expression
        return null
    }
}