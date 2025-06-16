// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.psi.findParameterWithName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter

internal class AddInlineModifierFix(
    parameter: KtParameter,
    modifier: KtModifierKeywordToken
) : AddModifierFix(parameter, modifier) {

    override fun getPresentation(context: ActionContext, element: KtModifierListOwner): Presentation =
        Presentation.of(KotlinBundle.message("fix.add.modifier.inline.parameter.text", modifier.value, element.name.toString()))

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.add.modifier.inline.parameter.family", modifier.value)

    object CrossInlineFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val casted = Errors.NON_LOCAL_RETURN_NOT_ALLOWED.cast(diagnostic)
            val reference = casted.a as? KtNameReferenceExpression ?: return null
            val parameter = reference.findParameterWithName(reference.getReferencedName()) ?: return null
            return AddInlineModifierFix(parameter, KtTokens.CROSSINLINE_KEYWORD).asIntention()
        }
    }

    object NoInlineFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val casted = when (diagnostic.factory) {
                Errors.USAGE_IS_NOT_INLINABLE -> Errors.USAGE_IS_NOT_INLINABLE.cast(diagnostic)
                Errors.USAGE_IS_NOT_INLINABLE_WARNING -> Errors.USAGE_IS_NOT_INLINABLE_WARNING.cast(diagnostic)
                else -> return null
            }
            val reference = casted.a as? KtNameReferenceExpression ?: return null
            val parameter = reference.findParameterWithName(reference.getReferencedName()) ?: return null
            return AddInlineModifierFix(parameter, KtTokens.NOINLINE_KEYWORD).asIntention()
        }
    }

    object NoInlineSuspendFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val parameter = diagnostic.psiElement as? KtParameter ?: return null
            return AddInlineModifierFix(parameter, KtTokens.NOINLINE_KEYWORD).asIntention()
        }
    }

    object CrossInlineSuspendFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val parameter = diagnostic.psiElement as? KtParameter ?: return null
            return AddInlineModifierFix(parameter, KtTokens.CROSSINLINE_KEYWORD).asIntention()
        }
    }

}