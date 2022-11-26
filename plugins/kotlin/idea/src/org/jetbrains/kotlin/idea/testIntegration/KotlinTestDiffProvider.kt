// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration

import com.intellij.execution.testframework.JvmTestDiffProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.asSafely
import com.siyeh.ig.testFrameworks.UAssertHint
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.util.findElementsOfClassInRange
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.toUElementOfType

class KotlinTestDiffProvider : JvmTestDiffProvider<KtCallExpression>() {
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
            call.valueArguments.getOrNull(argIndex) ?: return null
        }
        if (expr is KtStringTemplateEntry) return expr
        if (expr is KtReference) return expr.resolve()
        return null
    }
}