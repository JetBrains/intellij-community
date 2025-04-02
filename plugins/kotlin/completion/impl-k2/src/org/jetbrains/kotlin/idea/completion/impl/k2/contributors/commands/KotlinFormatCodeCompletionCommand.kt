// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.commands.AbstractFormatCodeCompletionCommand
import com.intellij.codeInsight.completion.command.commands.AbstractFormatCodeCompletionCommandProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression

internal class KotlinFormatCodeCompletionCommandProvider : AbstractFormatCodeCompletionCommandProvider() {
    override fun createCommand(context: CommandCompletionProviderContext): CompletionCommand {
        return KotlinFormatCodeCompletionCommand()
    }

}

internal class KotlinFormatCodeCompletionCommand : AbstractFormatCodeCompletionCommand() {
    override fun findTargetToRefactor(element: PsiElement): PsiElement {
        return element.parents(true).firstOrNull {
            (it is KtDeclaration || it is KtExpression)
        } ?: element.containingFile ?: element
    }
}