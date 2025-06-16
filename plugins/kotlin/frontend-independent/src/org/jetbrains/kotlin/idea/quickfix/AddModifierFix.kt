// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.*
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.psi.isInlineOrValue
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.quickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

open class AddModifierFix(
    element: KtModifierListOwner,
    protected val modifier: KtModifierKeywordToken,
) : PsiUpdateModCommandAction<KtModifierListOwner>(element) {
    override fun getPresentation(context: ActionContext, element: KtModifierListOwner): Presentation? =
        getPresentationImpl(element)

    fun getPresentationImpl(element: KtModifierListOwner): Presentation? {
        if (!element.canRefactorElement()) return null
        val actionName =
            if (modifier in modalityModifiers || modifier in KtTokens.VISIBILITY_MODIFIERS || modifier == KtTokens.CONST_KEYWORD) {
                KotlinBundle.message("fix.add.modifier.text", RemoveModifierFixBase.getElementName(element), modifier.value)
            } else {
                KotlinBundle.message("fix.add.modifier.text.generic", modifier.value)
            }
        return Presentation.of(actionName).withFixAllOption(this)
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.add.modifier.family")

    override fun invoke(
        context: ActionContext,
        element: KtModifierListOwner,
        updater: ModPsiUpdater,
    ) {
        element.addModifier(modifier)

        when (modifier) {
            KtTokens.ABSTRACT_KEYWORD -> {
                if (element is KtProperty || element is KtNamedFunction) {
                    element.containingClass()?.let { klass ->
                        if (!klass.hasModifier(KtTokens.ABSTRACT_KEYWORD) && !klass.hasModifier(KtTokens.SEALED_KEYWORD)) {
                            klass.addModifier(KtTokens.ABSTRACT_KEYWORD)
                        }
                    }
                }
            }

            KtTokens.OVERRIDE_KEYWORD -> {
                val visibility = element.visibilityModifierType()?.takeIf { it != KtTokens.PUBLIC_KEYWORD }
                visibility?.let { element.removeModifier(it) }
            }

            KtTokens.NOINLINE_KEYWORD ->
                element.removeModifier(KtTokens.CROSSINLINE_KEYWORD)
        }
    }

    interface Factory<T : ModCommandAction> {
        fun createFactory(modifier: KtModifierKeywordToken): QuickFixesPsiBasedFactory<PsiElement> {
            return createFactory(modifier, KtModifierListOwner::class.java)
        }

        fun <T : KtModifierListOwner> createFactory(
            modifier: KtModifierKeywordToken,
            modifierOwnerClass: Class<T>
        ): QuickFixesPsiBasedFactory<PsiElement> {
            return quickFixesPsiBasedFactory { e ->
                val modifierListOwner =
                    PsiTreeUtil.getParentOfType(e, modifierOwnerClass, false) ?: return@quickFixesPsiBasedFactory emptyList()
                listOfNotNull(createIfApplicable(modifierListOwner, modifier)?.asIntention())
            }
        }

        fun createIfApplicable(modifierListOwner: KtModifierListOwner, modifier: KtModifierKeywordToken): T? {
            when (modifier) {
                KtTokens.ABSTRACT_KEYWORD, KtTokens.OPEN_KEYWORD -> {
                    if (modifierListOwner is KtObjectDeclaration) return null
                    if (modifierListOwner is KtEnumEntry) return null
                    if (modifierListOwner is KtDeclaration && modifierListOwner !is KtClass) {
                        val parentClassOrObject = modifierListOwner.containingClassOrObject ?: return null
                        if (parentClassOrObject is KtObjectDeclaration) return null
                        if (parentClassOrObject is KtEnumEntry) return null
                    }
                    if (modifier == KtTokens.ABSTRACT_KEYWORD
                        && modifierListOwner is KtClass
                        && modifierListOwner.isInlineOrValue()
                    ) return null
                }
                KtTokens.INNER_KEYWORD -> {
                    if (modifierListOwner is KtObjectDeclaration) return null
                    if (modifierListOwner is KtClass) {
                        if (modifierListOwner.isInterface() ||
                            modifierListOwner.isSealed() ||
                            modifierListOwner.isEnum() ||
                            modifierListOwner.isData() ||
                            modifierListOwner.isAnnotation()
                        ) return null
                    }
                }
            }
            return createModifierFix(modifierListOwner, modifier)
        }

        fun createModifierFix(element: KtModifierListOwner, modifier: KtModifierKeywordToken): T
    }

    companion object : Factory<AddModifierFix> {
        val modifiersWithWarning: Set<KtModifierKeywordToken> = setOf(KtTokens.ABSTRACT_KEYWORD, KtTokens.FINAL_KEYWORD)
        private val modalityModifiers = modifiersWithWarning + KtTokens.OPEN_KEYWORD

        override fun createModifierFix(element: KtModifierListOwner, modifier: KtModifierKeywordToken): AddModifierFix =
            AddModifierFix(element, modifier)
    }
}
