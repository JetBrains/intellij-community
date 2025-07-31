// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractQuickDocumentationCompletionCommand
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver.Companion.tokenType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtUserType

class KotlinQuickDocumentationCompletionCommandProvider : AbstractQuickDocumentationCompletionCommand() {
    override fun findElement(offset: Int, psiFile: PsiFile): PsiElement? {
        var context = getCommandContext(offset, psiFile)
        if (context is PsiWhiteSpace) context = context.prevSibling
        if (context?.tokenType != KtTokens.IDENTIFIER) return null
        if (isDeclaration(context.parent)) return context
        if (context.parent is KtNameReferenceExpression &&
            context.parent?.parent is KtUserType
        ) {
            val type = (context.parent?.parent as? KtUserType) ?: return null
            val resolved = type.referenceExpression?.mainReference?.resolve()
            analyze(type) {
                if (isDeclaration(resolved) || resolved is PsiMember) return context
            }
        }

        val referenceExpression = context.parentOfType<KtNameReferenceExpression>()
        if (referenceExpression != null && analyze(referenceExpression) {
                val resolved = referenceExpression.mainReference.resolve()
                isDeclaration(resolved) || resolved is PsiMember
            }) {
            return referenceExpression
        }
        return null
    }

    private fun isDeclaration(element: PsiElement?): Boolean {
        return element is KtNamedDeclaration && !(element is KtProperty && element.isLocal)
    }
}