// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractTypeInfoCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsight.utils.expressionOrReturnType
import org.jetbrains.kotlin.idea.codeinsight.utils.shouldShowType
import org.jetbrains.kotlin.psi.KtExpression

internal class KotlinTypeInfoCompletionCommandProvider : AbstractTypeInfoCompletionCommandProvider() {
    override fun findElement(offset: Int, psiFile: PsiFile): PsiElement? {
        var context = getCommandContext(offset, psiFile)
        if (context is PsiWhiteSpace) context = context.prevSibling
        val expression = PsiTreeUtil.getParentOfType(context, KtExpression::class.java, false)
        if (expression != null && expression.shouldShowType() && analyze(expression) { expression.expressionOrReturnType() != null }) {
            return expression
        }
        return null
    }
}