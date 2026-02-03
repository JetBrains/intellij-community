// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix.Companion.modalityModifiers
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase.Companion.getElementName
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtModifierListOwner

class ChangeModifiersFix(
    element: KtModifierListOwner,
    private val removeModifier: KtModifierKeywordToken? = null,
    private val isRemovedModifierRedundant: Boolean = false,
    private val addModifier: KtModifierKeywordToken? = null,
) : PsiUpdateModCommandAction<KtModifierListOwner>(element) {

    override fun getPresentation(context: ActionContext, element: KtModifierListOwner): Presentation? {
        val actionName = when {
            addModifier != null && removeModifier != null -> {
                KotlinBundle.message(
                    "change.modifier.0.to.1",
                    removeModifier.value,
                    addModifier.value
                )
            }
            addModifier == null && removeModifier != null -> {
                when {
                    isRemovedModifierRedundant ->
                        KotlinBundle.message(
                            "remove.redundant.0.modifier",
                            removeModifier.value
                        )

                    removeModifier === KtTokens.ABSTRACT_KEYWORD || removeModifier === KtTokens.OPEN_KEYWORD ->
                        KotlinBundle.message(
                            "make.0.not.1",
                            getElementName(element),
                            removeModifier.value
                        )

                    else ->
                        KotlinBundle.message("remove.0.modifier", removeModifier.value)
                }
            }

            addModifier != null && removeModifier == null -> {
                if (addModifier in modalityModifiers || addModifier in KtTokens.VISIBILITY_MODIFIERS || addModifier == KtTokens.CONST_KEYWORD) {
                    KotlinBundle.message(
                        "fix.add.modifier.text",
                        getElementName(element),
                        addModifier.value
                    )
                } else {
                    KotlinBundle.message(
                        "fix.add.modifier.text.generic",
                        addModifier.value
                    )
                }
            }

            else -> return null
        }

        return Presentation.of(actionName).withFixAllOption(this)
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        when {
            addModifier == null && removeModifier != null -> KotlinBundle.message("remove.modifier")
            addModifier != null && removeModifier == null -> KotlinBundle.message("add.modifier")
            else -> KotlinBundle.message("change.modifier")
        }

    override fun invoke(context: ActionContext, element: KtModifierListOwner, updater: ModPsiUpdater) {
        addModifier?.let(element::addModifier)
        removeModifier?.let {
            RemoveModifierFixBase.removeModifier(element, it)
        }
    }

    companion object {
        fun removeModifierFix(element: KtModifierListOwner, modifier: KtModifierKeywordToken, isRedundant: Boolean = false): PsiUpdateModCommandAction<KtModifierListOwner> =
            ChangeModifiersFix(element, removeModifier = modifier, isRemovedModifierRedundant = isRedundant)

        fun addModifierFix(element: KtModifierListOwner, modifier: KtModifierKeywordToken): PsiUpdateModCommandAction<KtModifierListOwner> =
            ChangeModifiersFix(element, addModifier = modifier)
    }
}