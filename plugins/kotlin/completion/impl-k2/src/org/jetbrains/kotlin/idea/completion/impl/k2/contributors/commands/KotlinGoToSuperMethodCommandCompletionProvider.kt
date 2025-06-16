// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractGoToSuperMethodCompletionCommandProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinGoToSuperMethodCommandCompletionProvider : AbstractGoToSuperMethodCompletionCommandProvider() {
    override fun canGoToSuperMethod(element: PsiElement, offset: Int): Boolean {
        val namedFunction = element.parentOfType<KtNamedFunction>() ?: return false
        if (namedFunction.range.startOffset > offset ||
            (namedFunction.valueParameterList?.textRange?.endOffset ?: 0) < offset
        ) {
            return false
        }
        analyze(namedFunction) {
            return (namedFunction.symbol as? KaNamedFunctionSymbol)?.isOverride == true
        }
    }
}
