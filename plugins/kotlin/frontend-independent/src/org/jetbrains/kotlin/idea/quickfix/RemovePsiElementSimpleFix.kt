// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.isExplicitTypeReferenceNeededForTypeInference
import org.jetbrains.kotlin.idea.codeinsight.utils.removeProperty
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

open class RemovePsiElementSimpleFix private constructor(element: PsiElement, @Nls private val text: String) :
    PsiUpdateModCommandAction<PsiElement>(element) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.element")

    override fun getPresentation(context: ActionContext, element: PsiElement): Presentation = Presentation.of(text)

    override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater): Unit = element.delete()

    object RemoveImportFactory : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        public override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val directive = psiElement.getNonStrictParentOfType<KtImportDirective>() ?: return emptyList()
            val refText = directive.importedReference?.let { KotlinBundle.message("for.0", it.text) } ?: ""
            return listOf(
                RemovePsiElementSimpleFix(directive, KotlinBundle.message("remove.conflicting.import.0", refText)).asIntention()
            )
        }
    }

    object RemoveSpreadFactory : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        public override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            if (psiElement.node.elementType != KtTokens.MUL) return emptyList()
            return listOf(
                RemovePsiElementSimpleFix(psiElement, KotlinBundle.message("remove.star")).asIntention()
            )
        }
    }

    object RemoveTypeArgumentsFactory :
        QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        public override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val element = psiElement.getNonStrictParentOfType<KtTypeArgumentList>() ?: return emptyList()
            return listOf(
                RemovePsiElementSimpleFix(element, KotlinBundle.message("remove.type.arguments")).asIntention()
            )
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
            return listOf(
                RemovePsiElementSimpleFix(element, KotlinBundle.message("remove.type.parameters")).asIntention()
            )
        }
    }

    object RemoveVariableFactory : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        public override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            if (psiElement is KtDestructuringDeclarationEntry) return emptyList()
            val ktProperty = psiElement.getNonStrictParentOfType<KtProperty>() ?: return emptyList()

            val typeReference = ktProperty.typeReference
            if (typeReference != null && ktProperty.isExplicitTypeReferenceNeededForTypeInference(typeReference)) return emptyList()

            val removePropertyFix = object : RemovePsiElementSimpleFix(ktProperty, KotlinBundle.message("remove.variable.0", ktProperty.name.toString())) {
                override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater) {
                    val property = element as? KtProperty ?: return
                    removeProperty(property)
                }
            }

            return listOf(removePropertyFix.asIntention())
        }
    }
}
