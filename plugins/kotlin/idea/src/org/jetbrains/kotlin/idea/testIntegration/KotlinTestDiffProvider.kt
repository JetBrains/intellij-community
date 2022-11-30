// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration

import com.intellij.execution.testframework.JvmTestDiffProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.asSafely
import com.siyeh.ig.testFrameworks.UAssertHint
import org.jetbrains.kotlin.idea.util.findElementsOfClassInRange
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.toUElementOfType

class KotlinTestDiffProvider : JvmTestDiffProvider<KtCallExpression>() {
    override fun createActual(project: Project, element: PsiElement, actual: String): PsiElement {
        val factory = KtPsiFactory(project)
        if (element is KtStringTemplateEntry) return factory.createLiteralStringTemplateEntry(actual)
        return factory.createStringTemplate(actual)
    }

    override fun getParamIndex(param: PsiElement): Int? {
        if (param is KtParameter) {
            return param.parent.asSafely<KtParameterList>()?.parameters?.indexOf<PsiElement>(param)
        }
        return null
    }

    override fun getFailedCall(file: PsiFile, startOffset: Int, endOffset: Int): KtCallExpression? {
        val failedCallExpression = findElementsOfClassInRange(file, startOffset, endOffset, KtCallExpression::class.java).firstOrNull()
        return failedCallExpression.safeAs<KtCallExpression>()
    }

    override fun getExpected(call: KtCallExpression, argIndex: Int?): PsiElement? {
        val expr = if (argIndex == null) {
            val uCallElement = call.toUElementOfType<UCallExpression>() ?: return null
            UAssertHint.createAssertEqualsUHint(uCallElement)?.expected?.sourcePsi ?: return null
        } else {
            call.valueArguments.getOrNull(argIndex)?.getArgumentExpression() ?: return null
        }
        if (expr is KtStringTemplateExpression && expr.entries.size == 1) return expr.entries.first()
        if (expr is KtStringTemplateEntry) return expr
        if (expr is KtNameReferenceExpression) {
            val resolved = expr.reference?.resolve()
            if (resolved is KtVariableDeclaration) {
                val initializer = resolved.initializer
                if (initializer is KtStringTemplateExpression) return initializer
            }
            return resolved
        }
        return null
    }
}