// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.quickFixesPsiBasedFactory
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class ChangeVariableMutabilityFix(
    element: KtValVarKeywordOwner,
    private val makeVar: Boolean,
    @IntentionFamilyName private val actionText: String? = null,
    private val deleteInitializer: Boolean = false
) : PsiUpdateModCommandAction<KtValVarKeywordOwner>(element) {

    override fun getFamilyName(): @IntentionFamilyName String = actionText ?: buildString {
        if (makeVar) append(KotlinBundle.message("change.to.var")) else append(KotlinBundle.message("change.to.val"))
        if (deleteInitializer) append(KotlinBundle.message("and.delete.initializer"))
    }

    override fun getPresentation(context: ActionContext, element: KtValVarKeywordOwner): Presentation? {
        val valOrVar = element.valOrVarKeyword?.node?.elementType ?: return null
        return Presentation.of(familyName).takeIf {
            (valOrVar == KtTokens.VAR_KEYWORD) != makeVar
        }
    }

    override fun invoke(context: ActionContext, element: KtValVarKeywordOwner, updater: ModPsiUpdater) {
        val factory = KtPsiFactory(context.project)
        val newKeyword = if (makeVar) factory.createVarKeyword() else factory.createValKeyword()
        element.valOrVarKeyword!!.replace(newKeyword)
        if (deleteInitializer) {
            (element as? KtProperty)?.initializer = null
        }
        if (makeVar) {
            (element as? KtModifierListOwner)?.removeModifier(KtTokens.CONST_KEYWORD)
        }
    }

    companion object {

        val VAL_WITH_SETTER_FACTORY: QuickFixesPsiBasedFactory<KtPropertyAccessor> =
            quickFixesPsiBasedFactory { psiElement: KtPropertyAccessor ->
                listOf(ChangeVariableMutabilityFix(psiElement.property, true).asIntention())
            }

        val VAL_REASSIGNMENT: KotlinQuickFixFactory<KaFirDiagnostic.ValReassignment> =
            KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ValReassignment ->
                (diagnostic.variable.psi as? KtValVarKeywordOwner)?.takeIf(PsiElement::isWritable)?.let {
                    listOf(ChangeVariableMutabilityFix(it, makeVar = true))
                } ?: emptyList()
            }


        val VAR_OVERRIDDEN_BY_VAL_FACTORY: QuickFixesPsiBasedFactory<PsiElement> =
            quickFixesPsiBasedFactory { psiElement: PsiElement ->
                when (psiElement) {
                    is KtProperty, is KtParameter -> {
                        listOf(ChangeVariableMutabilityFix((psiElement as KtValVarKeywordOwner), true).asIntention())
                    }

                    else -> null
                } ?: emptyList()
            }

        val VAR_ANNOTATION_PARAMETER_FACTORY: QuickFixesPsiBasedFactory<KtParameter> =
            quickFixesPsiBasedFactory { psiElement: KtParameter ->
                listOf(ChangeVariableMutabilityFix(psiElement, false).asIntention())
            }

        val LATEINIT_VAL_FACTORY: QuickFixesPsiBasedFactory<KtModifierListOwner> =
            quickFixesPsiBasedFactory { psiElement: KtModifierListOwner ->
                (psiElement as? KtProperty)?.let {
                    if (it.valOrVarKeyword.text != "val") {
                        null
                    } else {
                        listOf(ChangeVariableMutabilityFix(it, makeVar = true).asIntention())
                    }
                } ?: emptyList()
            }

        val CONST_VAL_FACTORY: QuickFixesPsiBasedFactory<PsiElement> =
            quickFixesPsiBasedFactory { psiElement: PsiElement ->
                if (psiElement.node.elementType as? KtModifierKeywordToken != KtTokens.CONST_KEYWORD) return@quickFixesPsiBasedFactory emptyList()
                val property = psiElement.getStrictParentOfType<KtProperty>() ?: return@quickFixesPsiBasedFactory emptyList()
                listOf(ChangeVariableMutabilityFix(property, makeVar = false).asIntention())
            }

        val MUST_BE_INITIALIZED_FACTORY: QuickFixesPsiBasedFactory<PsiElement> =
            quickFixesPsiBasedFactory { psiElement: PsiElement ->
                val property = psiElement as? KtProperty ?: return@quickFixesPsiBasedFactory emptyList()
                val getter = property.getter ?: return@quickFixesPsiBasedFactory emptyList()
                if (!getter.hasBody()) return@quickFixesPsiBasedFactory emptyList()
                if (getter.hasBlockBody() && property.typeReference == null) return@quickFixesPsiBasedFactory emptyList()
                val setter = property.setter
                if (setter != null && setter.hasBody()) return@quickFixesPsiBasedFactory emptyList()
                listOf(ChangeVariableMutabilityFix(property, makeVar = false).asIntention())
            }

        val VOLATILE_ON_VALUE_FACTORY: QuickFixesPsiBasedFactory<KtAnnotationEntry> =
            quickFixesPsiBasedFactory { annotationEntry: KtAnnotationEntry ->
                val modifierList = annotationEntry.parent as? KtDeclarationModifierList ?: return@quickFixesPsiBasedFactory emptyList()
                val property = modifierList.parent as? KtProperty ?: return@quickFixesPsiBasedFactory emptyList()
                if (!property.isWritable || property.isLocal) return@quickFixesPsiBasedFactory emptyList()
                listOf(ChangeVariableMutabilityFix(property, makeVar = true).asIntention())
            }

        val VALUE_CLASS_CONSTRUCTOR_NOT_FINAL_READ_ONLY_PARAMETER_FACTORY: QuickFixesPsiBasedFactory<KtParameter> =
            quickFixesPsiBasedFactory { parameter: KtParameter ->
                if (!parameter.isMutable) return@quickFixesPsiBasedFactory emptyList()
                listOf(ChangeVariableMutabilityFix(parameter, makeVar = false).asIntention())
            }
    }
}