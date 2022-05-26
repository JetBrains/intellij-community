// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.util.parentOfTypes
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object CallFromPublicInlineFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val fixes = mutableListOf<IntentionAction>()
        val element = diagnostic.psiElement.safeAs<KtExpression>() ?: return fixes
        val (containingDeclaration, containingDeclarationName) = element.containingDeclaration() ?: return fixes
        when (diagnostic.factory) {
            Errors.NON_PUBLIC_CALL_FROM_PUBLIC_INLINE,
            Errors.PROTECTED_CALL_FROM_PUBLIC_INLINE.warningFactory,
            Errors.PROTECTED_CALL_FROM_PUBLIC_INLINE.errorFactory -> {
                val (declaration, declarationName, declarationVisibility) = element.referenceDeclaration() ?: return fixes
                fixes.add(ChangeVisibilityFix(containingDeclaration, containingDeclarationName, declarationVisibility))
                fixes.add(ChangeVisibilityFix(declaration, declarationName, KtTokens.PUBLIC_KEYWORD))
                if (!containingDeclaration.hasReifiedTypeParameter()) {
                    fixes.add(RemoveModifierFix(containingDeclaration, KtTokens.INLINE_KEYWORD, isRedundant = false))
                }
            }
            Errors.SUPER_CALL_FROM_PUBLIC_INLINE.warningFactory, Errors.SUPER_CALL_FROM_PUBLIC_INLINE.errorFactory -> {
                fixes.add(ChangeVisibilityFix(containingDeclaration, containingDeclarationName, KtTokens.INTERNAL_KEYWORD))
                fixes.add(ChangeVisibilityFix(containingDeclaration, containingDeclarationName, KtTokens.PRIVATE_KEYWORD))
                if (!containingDeclaration.hasReifiedTypeParameter()) {
                    fixes.add(RemoveModifierFix(containingDeclaration, KtTokens.INLINE_KEYWORD, isRedundant = false))
                }
            }
        }
        return fixes
    }

    private fun KtExpression.containingDeclaration(): Pair<KtCallableDeclaration, String>? {
        val declaration = parentOfTypes(KtNamedFunction::class, KtProperty::class)
            ?.safeAs<KtCallableDeclaration>()
            ?.takeIf { it.hasModifier(KtTokens.INLINE_KEYWORD) }
            ?: return null
        val name = declaration.name ?: return null
        return declaration to name
    }

    private fun KtExpression.referenceDeclaration(): Triple<KtDeclaration, String, KtModifierKeywordToken>? {
        val declaration = safeAs<KtNameReferenceExpression>()?.mainReference?.resolve().safeAs<KtDeclaration>() ?: return null
        val name = declaration.name ?: return null
        val visibility = declaration.visibilityModifierType() ?: return null
        return Triple(declaration, name, visibility)
    }

    private fun KtCallableDeclaration.hasReifiedTypeParameter() = typeParameters.any { it.hasModifier(KtTokens.REIFIED_KEYWORD) }
}
