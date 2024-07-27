/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.platform.has
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.JVM_INLINE_ANNOTATION_FQ_NAME

class InlineClassDeprecatedFix(
    element: KtModifierListOwner,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtModifierListOwner, Unit>(element, Unit) {

    @IntentionName
    private val text = KotlinBundle.message(
        "replace.with.0",
        (if (element.containingKtFile.hasJvmTarget()) "@JvmInline " else "") + "value"
    )

    override fun getPresentation(context: ActionContext, element: KtModifierListOwner): Presentation? = Presentation.of(text)

    override fun getFamilyName() = KotlinBundle.message("replace.modifier")

    override fun invoke(
        actionContext: ActionContext,
        element: KtModifierListOwner,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        element.removeModifier(KtTokens.INLINE_KEYWORD)
        element.addModifier(KtTokens.VALUE_KEYWORD)
        if (element.containingKtFile.hasJvmTarget()) {
            element.addAnnotation(JVM_INLINE_ANNOTATION_FQ_NAME)
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val deprecatedModifier = Errors.INLINE_CLASS_DEPRECATED.cast(diagnostic)
            val modifierListOwner = deprecatedModifier.psiElement.getParentOfType<KtModifierListOwner>(strict = true) ?: return null
            return if (deprecatedModifier != null) InlineClassDeprecatedFix(modifierListOwner).asIntention() else null
        }
    }

    private fun KtFile.hasJvmTarget(): Boolean = platform.has<JvmPlatform>()
}