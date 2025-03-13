// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.util.collectAllExpectAndActualDeclaration
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.INLINE_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.MODALITY_MODIFIERS
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner

/**
 * Similar to [AddModifierFix] but with multiplatform support.
 *
 * @param element The modifier list owner (e.g., a declaration) to which the modifier should be added.
 * @param modifier The modifier keyword to be added (supported modifiers are `abstract`, `final`, `sealed`, `open`, and `inline`).
 * Other modifiers do not appear to be multiplatform-persistent; in such cases, [AddModifierFix] should be used instead.
 */
internal class AddModifierFixMpp(
    private val element: KtModifierListOwner,
    private val modifier: KtModifierKeywordToken,
) : ModCommandAction {

    private val addModifierFix = object : AddModifierFix(element, modifier) {
        override fun getPresentation(context: ActionContext, element: KtModifierListOwner): Presentation =
            Presentation.of(KotlinBundle.message("action.text.continue"))

        override fun invoke(context: ActionContext, element: KtModifierListOwner, updater: ModPsiUpdater) {
            if (element !is KtDeclaration) throw IllegalArgumentException("KtDeclaration expected but ${element::class} found")
            val elementsToMutate = element.collectAllExpectAndActualDeclaration(withSelf = true).map(updater::getWritable)
            for (elementToMutate in elementsToMutate) {
                super.invoke(context, elementToMutate, updater)
            }
        }
    }

    private val cancelFix = object : ModCommandAction {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("action.text.cancel")
        override fun getPresentation(context: ActionContext): Presentation = Presentation.of(familyName)
        override fun perform(context: ActionContext): ModCommand = ModCommand.nop()
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.add.modifier.family")

    override fun getPresentation(context: ActionContext): Presentation? =
        addModifierFix.getPresentationImpl(element)

    override fun perform(context: ActionContext): ModCommand {
        return when {
            modifier in AddModifierFix.modifiersWithWarning ->
                ModCommand.chooseAction(
                    KotlinBundle.message("fix.potentially.broken.inheritance.message"),
                    listOf(addModifierFix, cancelFix)
                )

            modifier.isMultiplatformPersistent() -> addModifierFix.perform(context)
            else -> throw IllegalArgumentException(
                "'$modifier' is not multiplatform-persistent. Use 'org.jetbrains.kotlin.idea.quickfix.AddModifierFix' instead."
            )
        }
    }

    companion object : AddModifierFix.Factory<ModCommandAction> {
        override fun createModifierFix(element: KtModifierListOwner, modifier: KtModifierKeywordToken): ModCommandAction {
            return if (modifier in AddModifierFix.modifiersWithWarning || modifier.isMultiplatformPersistent()) {
                AddModifierFixMpp(element, modifier)
            } else {
                AddModifierFix(element, modifier)
            }
        }
    }
}

fun KtModifierKeywordToken.isMultiplatformPersistent(): Boolean =
    this in MODALITY_MODIFIERS || this == INLINE_KEYWORD
