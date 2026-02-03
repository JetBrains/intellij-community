// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories.containingDeclaration
import org.jetbrains.kotlin.idea.quickfix.ChangeModifiersFix.Companion.removeModifierFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtExpression

internal object CallFromPublicInlineFixFactories {

    val nonPublicCallFromPublicInlineFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NonPublicCallFromPublicInline ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.psi)
        )
    }

    val protectedCallFromPublicInlineErrorFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ProtectedCallFromPublicInlineError ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.psi)
        )
    }

    val superCallFromPublicInlineFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.SuperCallFromPublicInline ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.psi)
        )
    }

    private fun createFixIfAvailable(element: PsiElement?): PsiUpdateModCommandAction<*>? {
        val expression = element as? KtExpression ?: return null
        val containingDeclaration = expression.containingDeclaration() ?: return null
        if (containingDeclaration.hasReifiedTypeParameter()) return null

        return removeModifierFix(containingDeclaration, KtTokens.INLINE_KEYWORD)
    }

    private fun KtCallableDeclaration.hasReifiedTypeParameter() = typeParameters.any { it.hasModifier(KtTokens.REIFIED_KEYWORD) }
}
