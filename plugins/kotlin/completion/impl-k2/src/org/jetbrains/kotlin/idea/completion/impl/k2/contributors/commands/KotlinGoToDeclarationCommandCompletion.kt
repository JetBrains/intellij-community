// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractGoToDeclarationCompletionCommand
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.utils.printer.parentOfType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver.Companion.tokenType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtReferenceExpression

class KotlinGoToDeclarationCommandCompletion : AbstractGoToDeclarationCompletionCommand() {
    override fun canNavigateToDeclaration(context: PsiElement): Boolean {
        if (context.tokenType != KtTokens.IDENTIFIER) return false
        val ref = context.parentOfType<KtReferenceExpression>() ?: return false
        analyze(ref) {
            return ref.mainReference.resolve() != null
        }
    }
}