// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories.containingDeclaration
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtExpression

internal object CallFromPublicInlineFixFactories {

    val nonPublicCallFromPublicInlineFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.NonPublicCallFromPublicInline ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.psi)
        )
    }

    val protectedCallFromPublicInlineErrorFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ProtectedCallFromPublicInlineError ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.psi)
        )
    }

    val superCallFromPublicInlineFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.SuperCallFromPublicInline ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.psi)
        )
    }

    private fun createFixIfAvailable(element: PsiElement?): RemoveModifierFixBase? {
        val expression = element as? KtExpression ?: return null
        val containingDeclaration = expression.containingDeclaration() ?: return null
        if (containingDeclaration.hasReifiedTypeParameter()) return null

        return RemoveModifierFixBase(containingDeclaration, KtTokens.INLINE_KEYWORD, isRedundant = false)
    }

    private fun KtCallableDeclaration.hasReifiedTypeParameter() = typeParameters.any { it.hasModifier(KtTokens.REIFIED_KEYWORD) }
}
