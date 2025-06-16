// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.commands.AbstractFormatCodeCompletionCommand
import com.intellij.codeInsight.completion.command.commands.AbstractFormatCodeCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction

internal class KotlinFormatCodeCompletionCommandProvider : AbstractFormatCodeCompletionCommandProvider() {
    override fun createCommand(context: CommandCompletionProviderContext): CompletionCommand? {
        val element = getCommandContext(context.offset, context.psiFile) ?: return null
        val targetElement = findTargetToRefactorInner(element)
        val highlightInfoLookup = HighlightInfoLookup(targetElement.textRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
        val command = object : KotlinFormatCodeCompletionCommand() {
            override val highlightInfo: HighlightInfoLookup
                get() {
                    return highlightInfoLookup
                }
        }
        return command
    }
}

private fun findTargetToRefactorInner(element: PsiElement): PsiElement {
    val psiElement = element.parents(true).firstOrNull {
        (it is KtDeclaration || it is KtExpression)
    } ?: element.containingFile
    if(psiElement.parent is KtFunction) return psiElement.parent
    return psiElement ?: element
}


internal abstract class KotlinFormatCodeCompletionCommand : AbstractFormatCodeCompletionCommand() {
    override fun findTargetToRefactor(element: PsiElement): PsiElement {
        return findTargetToRefactorInner(element)
    }
}