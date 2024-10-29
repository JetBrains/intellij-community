// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.psi.isInlineOrValue
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.quickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.inspections.KotlinUniversalQuickFix
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

open class AddModifierFix(
    element: KtModifierListOwner,
    @SafeFieldForPreview
    protected val modifier: KtModifierKeywordToken
) : KotlinCrossLanguageQuickFixAction<KtModifierListOwner>(element), KotlinUniversalQuickFix {
    override fun getText(): String {
        val element = element ?: return ""
        if (modifier in modalityModifiers || modifier in KtTokens.VISIBILITY_MODIFIERS || modifier == KtTokens.CONST_KEYWORD) {
            return KotlinBundle.message("fix.add.modifier.text", RemoveModifierFixBase.getElementName(element), modifier.value)
        }
        return KotlinBundle.message("fix.add.modifier.text.generic", modifier.value)
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.add.modifier.family")

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? {
        return element?.containingFile
    }

    protected fun invokeOnElement(element: KtModifierListOwner?) {
        if (element == null) return

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

    override fun invokeImpl(project: Project, editor: Editor?, file: PsiFile) {
        val originalElement = element
        invokeOnElement(originalElement)
    }

    // TODO: consider checking if this fix is available by testing if the [element] can be refactored by calling
    //  FIR version of [org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringUtilKt#canRefactor]
    override fun isAvailableImpl(project: Project, editor: Editor?, file: PsiFile): Boolean = element != null

    interface Factory<T : AddModifierFix> {
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
                listOfNotNull(createIfApplicable(modifierListOwner, modifier))
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
        val addAbstractModifier = AddModifierFix.createFactory(KtTokens.ABSTRACT_KEYWORD)
        val addAbstractToContainingClass = AddModifierFix.createFactory(KtTokens.ABSTRACT_KEYWORD, KtClassOrObject::class.java)
        val addOpenToContainingClass = AddModifierFix.createFactory(KtTokens.OPEN_KEYWORD, KtClassOrObject::class.java)
        val addFinalToProperty = AddModifierFix.createFactory(KtTokens.FINAL_KEYWORD, KtProperty::class.java)
        val addInnerModifier = createFactory(KtTokens.INNER_KEYWORD)
        val addOverrideModifier = createFactory(KtTokens.OVERRIDE_KEYWORD)
        val addDataModifier = createFactory(KtTokens.DATA_KEYWORD, KtClass::class.java)
        val addInlineToFunctionWithReified = createFactory(KtTokens.INLINE_KEYWORD, KtNamedFunction::class.java)

        val modifiersWithWarning: Set<KtModifierKeywordToken> = setOf(KtTokens.ABSTRACT_KEYWORD, KtTokens.FINAL_KEYWORD)
        private val modalityModifiers = modifiersWithWarning + KtTokens.OPEN_KEYWORD

        override fun createModifierFix(element: KtModifierListOwner, modifier: KtModifierKeywordToken): AddModifierFix =
            AddModifierFix(element, modifier)
    }
}
