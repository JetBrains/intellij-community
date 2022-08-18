// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class AddInlineModifierFix(
    parameter: KtParameter,
    modifier: KtModifierKeywordToken
) : AddModifierFixFE10(parameter, modifier) {

    override fun getText(): String {
        val element = this.element
        return when {
            element != null -> KotlinBundle.message("fix.add.modifier.inline.parameter.text", modifier.value, element.name.toString())
            else -> null
        } ?: ""
    }

    override fun getFamilyName() = KotlinBundle.message("fix.add.modifier.inline.parameter.family", modifier.value)

    companion object {
        private fun KtElement.findParameterWithName(name: String): KtParameter? {
            val function = getStrictParentOfType<KtFunction>() ?: return null
            return function.valueParameters.firstOrNull { it.name == name } ?: function.findParameterWithName(name)
        }
    }

    object CrossInlineFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val casted = Errors.NON_LOCAL_RETURN_NOT_ALLOWED.cast(diagnostic)
            val reference = casted.a as? KtNameReferenceExpression ?: return null
            val parameter = reference.findParameterWithName(reference.getReferencedName()) ?: return null
            return AddInlineModifierFix(parameter, KtTokens.CROSSINLINE_KEYWORD)
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
            return AddInlineModifierFix(parameter, KtTokens.NOINLINE_KEYWORD)
        }
    }

    object NoInlineSuspendFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val parameter = diagnostic.psiElement as? KtParameter ?: return null
            return AddInlineModifierFix(parameter, KtTokens.NOINLINE_KEYWORD)
        }
    }

    object CrossInlineSuspendFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val parameter = diagnostic.psiElement as? KtParameter ?: return null
            return AddInlineModifierFix(parameter, KtTokens.CROSSINLINE_KEYWORD)
        }
    }

}