// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.util.parentOfTypes
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object NonPublicCallFromPublicInlineFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val element = diagnostic.psiElement.safeAs<KtNameReferenceExpression>() ?: return emptyList()

        val containingDeclaration = element.parentOfTypes(KtNamedFunction::class, KtProperty::class)
            ?.safeAs<KtCallableDeclaration>()
            ?.takeIf { it.hasModifier(KtTokens.INLINE_KEYWORD) }
            ?: return emptyList()
        val containingDeclarationName = containingDeclaration.name ?: return emptyList()

        val declaration = element.mainReference.resolve().safeAs<KtDeclaration>() ?: return emptyList()
        val declarationVisibility = declaration.visibilityModifierType() ?: return emptyList()
        val declarationName = declaration.name ?: return emptyList()

        val fixes = mutableListOf<IntentionAction>(
            ChangeVisibilityFix(containingDeclaration, containingDeclarationName, declarationVisibility),
            ChangeVisibilityFix(declaration, declarationName, KtTokens.PUBLIC_KEYWORD)
        )
        if (!containingDeclaration.hasReifiedTypeParameter()) {
            fixes.add(RemoveModifierFix(containingDeclaration, KtTokens.INLINE_KEYWORD, isRedundant = false))
        }
        return fixes
    }

    private fun KtCallableDeclaration.hasReifiedTypeParameter() = typeParameters.any { it.hasModifier(KtTokens.REIFIED_KEYWORD) }
}