// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.collectAllExpectAndActualDeclaration
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Similar to [AddModifierFix] but with multiplatform support.
 *
 * @param element The modifier list owner (e.g., a declaration) to which the modifier should be added.
 * @param modifier The modifier keyword to be added (supported modifiers are `abstract`, `final`, `sealed`, `open`, and `inline`).
 * Other modifiers do not appear to be multiplatform-persistent; in such cases, [AddModifierFix] should be used instead.
 */
class AddModifierFixMpp(
    private val element: KtModifierListOwner,
    private val modifier: KtModifierKeywordToken,
) : ModCommandAction {

    private val addModifierFix = object : AddModifierFix(element, modifier) {
        override fun getPresentation(context: ActionContext, element: KtModifierListOwner): Presentation =
            Presentation.of(KotlinBundle.message("action.text.continue"))

        override fun invoke(context: ActionContext, element: KtModifierListOwner, updater: ModPsiUpdater) {
            if (element !is KtDeclaration) throw IllegalArgumentException("KtDeclaration expected but ${element::class} found")
            val declaration = PsiTreeUtil.findSameElementInCopy(element, element.containingFile.originalFile)
            val elementsToMutate = declaration.collectAllExpectAndActualDeclaration(withSelf = true).map(updater::getWritable)
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
        val addAbstractModifier: QuickFixesPsiBasedFactory<PsiElement> = createFactory(KtTokens.ABSTRACT_KEYWORD)
        val addAbstractToContainingClass: QuickFixesPsiBasedFactory<PsiElement> = createFactory(KtTokens.ABSTRACT_KEYWORD, KtClassOrObject::class.java)
        val addOpenToContainingClass: QuickFixesPsiBasedFactory<PsiElement> = createFactory(KtTokens.OPEN_KEYWORD, KtClassOrObject::class.java)
        val addFinalToProperty: QuickFixesPsiBasedFactory<PsiElement> = createFactory(KtTokens.FINAL_KEYWORD, KtProperty::class.java)
        val addInnerModifier: QuickFixesPsiBasedFactory<PsiElement> = createFactory(KtTokens.INNER_KEYWORD)
        val addOverrideModifier: QuickFixesPsiBasedFactory<PsiElement> = createFactory(KtTokens.OVERRIDE_KEYWORD)
        val addDataModifier: QuickFixesPsiBasedFactory<PsiElement> = createFactory(KtTokens.DATA_KEYWORD, KtClass::class.java)
        val addInlineToFunctionWithReified: QuickFixesPsiBasedFactory<PsiElement> = createFactory(KtTokens.INLINE_KEYWORD, KtNamedFunction::class.java)

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
    this in KtTokens.MODALITY_MODIFIERS || this == KtTokens.INLINE_KEYWORD