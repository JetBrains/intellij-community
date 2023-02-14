// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration

import com.intellij.execution.testframework.JvmTestDiffProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiType
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import com.siyeh.ig.testFrameworks.UAssertHint
import org.jetbrains.kotlin.backend.jvm.ir.psiElement
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.util.findElementsOfClassInRange
import org.jetbrains.kotlin.psi.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.toUElementOfType

class KotlinTestDiffProvider : JvmTestDiffProvider() {
    override fun failedCall(file: PsiFile, startOffset: Int, endOffset: Int, method: UMethod?): PsiElement? {
        val failedCalls = findElementsOfClassInRange(file, startOffset, endOffset, KtCallExpression::class.java)
            .map { it as KtCallExpression }
        if (failedCalls.isEmpty()) return null
        if (failedCalls.size == 1) return failedCalls.first()
        if (method == null) return null
        return failedCalls.firstOrNull { it.resolveToCall()?.resultingDescriptor?.psiElement?.isEquivalentTo(method.sourcePsi) == true }
    }

    override fun getExpected(call: PsiElement, param: UParameter?): PsiElement? {
        if (call !is KtCallExpression) return null
        val expr = if (param == null) {
            val uCallElement = call.toUElementOfType<UCallExpression>() ?: return null
            val assertHint = UAssertHint.createAssertEqualsHint(uCallElement) ?: return null
            if (assertHint.expected.getExpressionType() != PsiType.getJavaLangString(call.manager, call.resolveScope)) return null
            if (assertHint.actual.getExpressionType() != PsiType.getJavaLangString(call.manager, call.resolveScope)) return null
            assertHint.expected.sourcePsi ?: return null
        } else {
            val argument = call.valueArguments.firstOrNull {it.getArgumentName()?.asName?.asString() == param.name } ?: let {
                val srcParam = param.sourcePsi?.asSafely<KtParameter>()
                val paramList = srcParam?.parentOfType<KtParameterList>()
                val argIndex = paramList?.parameters?.indexOf<PsiElement>(srcParam)
                if (argIndex != null && argIndex != -1) call.valueArguments.getOrNull(argIndex) else null
            }
            argument?.getArgumentExpression()
        }
        if (expr is KtStringTemplateExpression && expr.entries.size == 1) return expr
        if (expr is KtStringTemplateEntry) return expr.parent
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