// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class ReplaceModifierFix(
    element: KtModifierListOwner,
    private val replacement: KtModifierKeywordToken
) : KotlinQuickFixAction<KtModifierListOwner>(element), CleanupFix {

    @Nls
    private val text = KotlinBundle.message("replace.with.0", replacement.value)

    override fun getText() = text

    override fun getFamilyName() = KotlinBundle.message("replace.modifier")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.addModifier(replacement)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val deprecatedModifier = Errors.DEPRECATED_MODIFIER.cast(diagnostic)
            val modifier = deprecatedModifier.a
            val replacement = deprecatedModifier.b
            val modifierListOwner = deprecatedModifier.psiElement.getParentOfType<KtModifierListOwner>(strict = true) ?: return null
            return when (modifier) {
                KtTokens.HEADER_KEYWORD, KtTokens.IMPL_KEYWORD -> ReplaceModifierFix(modifierListOwner, replacement)
                else -> null
            }
        }
    }
}