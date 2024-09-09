// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext

class InlineTypeParameterFix(
    element: KtTypeReference,
    private val typeReferencesToInline: List<SmartPsiElementPointer<KtTypeReference>>,
) : PsiUpdateModCommandAction<KtTypeReference>(element) {

    private data class ElementContext(
        val parameter: KtTypeParameter,
        val bound: KtTypeReference,
        val constraint: KtElement?,
    )

    override fun invoke(
        actionContext: ActionContext,
        element: KtTypeReference,
        updater: ModPsiUpdater,
    ) {
        val parameterListOwner = element.getStrictParentOfType<KtTypeParameterListOwner>() ?: return
        val parameterList = parameterListOwner.typeParameterList ?: return
        val (parameter, bound, constraint) = prepareElementContext(element, parameterList) ?: return

        typeReferencesToInline.mapNotNull { it.element }.map(updater::getWritable).forEach { it.replace(bound) }

        if (parameterList.parameters.size == 1) {
            parameterList.delete()
            val constraintList = parameterListOwner.typeConstraintList
            if (constraintList != null) {
                constraintList.siblings(forward = false).firstOrNull { it.elementType == KtTokens.WHERE_KEYWORD }?.delete()
                constraintList.delete()
            }
        } else {
            EditCommaSeparatedListHelper.removeItem(parameter)
            if (constraint != null) {
                EditCommaSeparatedListHelper.removeItem(constraint)
            }
        }
    }

    override fun getFamilyName() = KotlinBundle.message("inline.type.parameter")

    companion object Factory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val element = Errors.FINAL_UPPER_BOUND.cast(diagnostic).psiElement
            val parameterListOwner = element.getStrictParentOfType<KtTypeParameterListOwner>() ?: return null
            val parameterList = parameterListOwner.typeParameterList ?: return null
            val (parameter, _, _) = prepareElementContext(element, parameterList) ?: return null

            val context = parameterListOwner.analyzeWithContent()
            val parameterDescriptor = context[BindingContext.TYPE_PARAMETER, parameter] ?: return null

            val typeReferencesToInline = parameterListOwner
                .descendantsOfType<KtTypeReference>()
                .filter { typeReference ->
                    val typeElement = typeReference.typeElement
                    val type = context[BindingContext.TYPE, typeReference]
                    typeElement != null && type?.constructor?.declarationDescriptor == parameterDescriptor
                }.map { it.createSmartPointer() }
                .toList()

            return InlineTypeParameterFix(element, typeReferencesToInline).asIntention()
        }

        private fun prepareElementContext(
            element: KtTypeReference,
            parameterList: KtTypeParameterList
        ): ElementContext? {
            return when (val parent = element.parent) {
                is KtTypeParameter -> {
                    val bound = parent.extendsBound ?: return null
                    ElementContext(parent, bound, null)
                }

                is KtTypeConstraint -> {
                    val subjectTypeParameterName = parent.subjectTypeParameterName?.text ?: return null
                    val parameter = parameterList.parameters.firstOrNull { it.name == subjectTypeParameterName } ?: return null
                    val bound = parent.boundTypeReference ?: return null
                    ElementContext(parameter, bound, parent)
                }

                else -> null
            }
        }
    }
}