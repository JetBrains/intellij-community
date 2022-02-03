// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.resolvedToArrayType
import org.jetbrains.kotlin.idea.util.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall

class ReplaceArrayEqualityOpWithArraysEqualsInspection : AbstractApplicabilityBasedInspection<KtBinaryExpression>(
    KtBinaryExpression::class.java
) {
    override fun applyTo(element: KtBinaryExpression, project: Project, editor: Editor?) {
        val right = element.right ?: return
        val left = element.left ?: return
        val factory = KtPsiFactory(project)
        val template = buildString {
            if (element.operationToken == KtTokens.EXCLEQ) append("!")
            append("$0.contentEquals($1)")
        }
        element.replace(factory.createExpressionByPattern(template, left, right))
    }

    override fun isApplicable(element: KtBinaryExpression): Boolean {
        when (element.operationToken) {
            KtTokens.EQEQ, KtTokens.EXCLEQ -> {
            }
            else -> return false
        }
        val right = element.right
        val left = element.left
        if (right == null || left == null) return false
        val context = element.safeAnalyzeNonSourceRootCode()
        val rightResolvedCall = right.getResolvedCall(context)
        val leftResolvedCall = left.getResolvedCall(context)
        return rightResolvedCall?.resolvedToArrayType() == true && leftResolvedCall?.resolvedToArrayType() == true
    }

    override fun inspectionText(element: KtBinaryExpression) = KotlinBundle.message("dangerous.array.comparison")

    override val defaultFixText: String get() = KotlinBundle.message("replace.with.contentequals3")

    override fun fixText(element: KtBinaryExpression): String = when (element.operationToken) {
        KtTokens.EQEQ -> KotlinBundle.message("replace.with.contentequals2")
        KtTokens.EXCLEQ -> KotlinBundle.message("replace.with.contentequals")
        else -> ""
    }
}
