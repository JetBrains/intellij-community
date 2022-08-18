/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.isExplicitTypeReferenceNeededForTypeInference
import org.jetbrains.kotlin.idea.codeinsight.utils.removeProperty
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

open class RemovePsiElementSimpleFix private constructor(element: PsiElement, @Nls private val text: String) :
    KotlinPsiOnlyQuickFixAction<PsiElement>(element) {
    override fun getFamilyName() = KotlinBundle.message("remove.element")

    override fun getText() = text

    public override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.delete()
    }

    object RemoveImportFactory : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        public override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val directive = psiElement.getNonStrictParentOfType<KtImportDirective>() ?: return emptyList()
            val refText = directive.importedReference?.let { KotlinBundle.message("for.0", it.text) } ?: ""
            return listOf(RemovePsiElementSimpleFix(directive, KotlinBundle.message("remove.conflicting.import.0", refText)))
        }
    }

    object RemoveSpreadFactory : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        public override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val element = psiElement
            if (element.node.elementType != KtTokens.MUL) return emptyList()
            return listOf(RemovePsiElementSimpleFix(element, KotlinBundle.message("remove.star")))
        }
    }

    object RemoveTypeArgumentsFactory :
        QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        public override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val element = psiElement.getNonStrictParentOfType<KtTypeArgumentList>() ?: return emptyList()
            return listOf(RemovePsiElementSimpleFix(element, KotlinBundle.message("remove.type.arguments")))
        }
    }

    object RemoveTypeParametersFactory :
        QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        public override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            // FIR passes the KtProperty while FE1.0 passes the type parameter list.
            val element = if (psiElement is KtProperty) {
                psiElement.typeParameterList
            } else {
                psiElement.getNonStrictParentOfType<KtTypeParameterList>()
            } ?: return emptyList()
            return listOf(RemovePsiElementSimpleFix(element, KotlinBundle.message("remove.type.parameters")))
        }
    }

    object RemoveVariableFactory : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        public override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            if (psiElement is KtDestructuringDeclarationEntry) return emptyList()
            val ktProperty = psiElement.getNonStrictParentOfType<KtProperty>() ?: return emptyList()
            if (ktProperty.isExplicitTypeReferenceNeededForTypeInference()) return emptyList()

            val removePropertyFix = object : RemovePsiElementSimpleFix(ktProperty, KotlinBundle.message("remove.variable.0", ktProperty.name.toString())) {
                override fun invoke(project: Project, editor: Editor?, file: KtFile) {
                    val property = element as? KtProperty ?: return
                    removeProperty(property)
                }
            }

            return listOf(removePropertyFix)
        }
    }
}
