// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractShowUsagesActionCompletionCommandProvider
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.util.CommentSaver.Companion.tokenType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedDeclaration

internal class KotlinShowUsagesActionCompletionCommandProvider : AbstractShowUsagesActionCompletionCommandProvider() {
    override fun hasToShow(element: PsiElement): Boolean {
        if (element.tokenType != KtTokens.IDENTIFIER) return false
        return element.parent is KtNamedDeclaration
    }
}