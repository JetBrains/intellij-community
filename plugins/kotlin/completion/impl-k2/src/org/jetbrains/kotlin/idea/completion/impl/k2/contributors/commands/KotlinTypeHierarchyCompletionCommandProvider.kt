// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractTypeHierarchyCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver.Companion.tokenType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtUserType

class KotlinTypeHierarchyCompletionCommandProvider : AbstractTypeHierarchyCompletionCommandProvider() {
    override fun findElement(offset: Int, psiFile: PsiFile): PsiElement? {
        var context = getCommandContext(offset, psiFile)
        if (context is PsiWhiteSpace) context = context.prevSibling
        if (context?.tokenType == KtTokens.IDENTIFIER && context.parent is KtClass) return context
        if (context?.tokenType == KtTokens.IDENTIFIER &&
            context.parent is KtNameReferenceExpression &&
            context.parent?.parent is KtUserType
        ) {
            val type = (context.parent?.parent as? KtUserType) ?: return null
            analyze(type) {
                val resolved = type.referenceExpression?.mainReference?.resolve()
                if (resolved is KtClass || resolved is PsiClass) return context
            }
        }
        return null
    }
}