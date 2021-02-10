// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.quickfix.QuickFixUtil
import org.jetbrains.kotlin.idea.inspections.KotlinUniversalQuickFix
import org.jetbrains.kotlin.idea.refactoring.canRefactor
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.idea.util.collectAllExpectAndActualDeclaration
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.TypeUtils

open class AddModifierFix(
    element: KtModifierListOwner,
    protected val modifier: KtModifierKeywordToken
) : KotlinCrossLanguageQuickFixAction<KtModifierListOwner>(element), KotlinUniversalQuickFix {
    override fun getText(): String {
        val element = element ?: return ""
        if (modifier in modalityModifiers || modifier in VISIBILITY_MODIFIERS || modifier == CONST_KEYWORD) {
            return KotlinBundle.message("fix.add.modifier.text", RemoveModifierFix.getElementName(element), modifier.value)
        }
        return KotlinBundle.message("fix.add.modifier.text.generic", modifier.value)
    }

    override fun getFamilyName() = KotlinBundle.message("fix.add.modifier.family")

    private fun invokeOnElement(element: KtModifierListOwner?) {
        element?.addModifier(modifier)

        if (modifier == ABSTRACT_KEYWORD && (element is KtProperty || element is KtNamedFunction)) {
            element.containingClass()?.run {
                if (!hasModifier(ABSTRACT_KEYWORD) && !hasModifier(SEALED_KEYWORD)) {
                    addModifier(ABSTRACT_KEYWORD)
                }
            }
        }
    }

    override fun startInWriteAction(): Boolean = !modifier.isMultiplatformPersistent() ||
            (element?.hasActualModifier() != true && element?.hasExpectModifier() != true)

    override fun invokeImpl(project: Project, editor: Editor?, file: PsiFile) {
        val originalElement = element
        if (originalElement is KtDeclaration && modifier.isMultiplatformPersistent()) {
            val elementsToMutate = originalElement.collectAllExpectAndActualDeclaration(withSelf = true)
            if (elementsToMutate.size > 1 && modifier in modifiersWithWarning) {
                val dialog = MessageDialogBuilder.okCancel(
                    KotlinBundle.message("fix.potentially.broken.inheritance.title"),
                    KotlinBundle.message("fix.potentially.broken.inheritance.message"),
                ).asWarning()

                if (!dialog.ask(project)) return
            }

            runWriteAction {
                for (declaration in elementsToMutate) {
                    invokeOnElement(declaration)
                }
            }
        } else {
            invokeOnElement(originalElement)
        }
    }

    override fun isAvailableImpl(project: Project, editor: Editor?, file: PsiFile): Boolean {
        val element = element ?: return false
        return element.canRefactor()
    }

    companion object {

        private fun KtModifierKeywordToken.isMultiplatformPersistent(): Boolean =
            this in MODALITY_MODIFIERS || this == INLINE_KEYWORD

        private val modifiersWithWarning = setOf(ABSTRACT_KEYWORD, FINAL_KEYWORD)

        private val modalityModifiers = modifiersWithWarning + OPEN_KEYWORD

        fun createFactory(modifier: KtModifierKeywordToken): KotlinSingleIntentionActionFactory {
            return createFactory(modifier, KtModifierListOwner::class.java)
        }

        fun <T : KtModifierListOwner> createFactory(
            modifier: KtModifierKeywordToken,
            modifierOwnerClass: Class<T>
        ): KotlinSingleIntentionActionFactory {
            return object : KotlinSingleIntentionActionFactory() {
                public override fun createAction(diagnostic: Diagnostic): IntentionAction? {
                    val modifierListOwner = QuickFixUtil.getParentElementOfType(diagnostic, modifierOwnerClass) ?: return null
                    return createIfApplicable(modifierListOwner, modifier)
                }
            }
        }

        fun createIfApplicable(modifierListOwner: KtModifierListOwner, modifier: KtModifierKeywordToken): AddModifierFix? {
            when (modifier) {
                ABSTRACT_KEYWORD, OPEN_KEYWORD -> {
                    if (modifierListOwner is KtObjectDeclaration) return null
                    if (modifierListOwner is KtEnumEntry) return null
                    if (modifierListOwner is KtDeclaration && modifierListOwner !is KtClass) {
                        val parentClassOrObject = modifierListOwner.containingClassOrObject ?: return null
                        if (parentClassOrObject is KtObjectDeclaration) return null
                        if (parentClassOrObject is KtEnumEntry) return null
                    }
                    if (modifier == ABSTRACT_KEYWORD
                        && modifierListOwner is KtClass
                        && modifierListOwner.hasModifier(INLINE_KEYWORD)
                    ) return null
                }
                INNER_KEYWORD -> {
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
            return AddModifierFix(modifierListOwner, modifier)
        }

    }

    object MakeClassOpenFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val typeReference = diagnostic.psiElement as KtTypeReference
            val declaration = typeReference.classForRefactor() ?: return null
            if (declaration.isEnum() || declaration.isData()) return null
            return AddModifierFix(declaration, OPEN_KEYWORD)
        }
    }

    object AddLateinitFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val property = Errors.MUST_BE_INITIALIZED_OR_BE_ABSTRACT.cast(diagnostic).psiElement
            if (!property.isVar) return null

            val descriptor = property.resolveToDescriptorIfAny(BodyResolveMode.FULL) ?: return null
            val type = (descriptor as? PropertyDescriptor)?.type ?: return null

            if (TypeUtils.isNullableType(type)) return null
            if (KotlinBuiltIns.isPrimitiveType(type)) return null

            return AddModifierFix(property, LATEINIT_KEYWORD)
        }
    }
}

fun KtTypeReference.classForRefactor(): KtClass? {
    val bindingContext = analyze(BodyResolveMode.PARTIAL)
    val type = bindingContext[BindingContext.TYPE, this] ?: return null
    val classDescriptor = type.constructor.declarationDescriptor as? ClassDescriptor ?: return null
    val declaration = DescriptorToSourceUtils.descriptorToDeclaration(classDescriptor) as? KtClass ?: return null
    if (!declaration.canRefactor()) return null
    return declaration
}