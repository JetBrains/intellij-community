// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class InlineTypeParameterFix(
    element: KtTypeReference,
    private val typeReferencesToInline: List<SmartPsiElementPointer<KtTypeReference>>,
) : PsiUpdateModCommandAction<KtTypeReference>(element) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtTypeReference,
        updater: ModPsiUpdater,
    ) {
        val parameterListOwner = element.getStrictParentOfType<KtTypeParameterListOwner>() ?: return
        val parameterList = parameterListOwner.typeParameterList ?: return
        val (parameter, bound, constraint) = prepareInlineTypeParameterContext(element, parameterList) ?: return

        val writableTypeReferences = typeReferencesToInline.mapNotNull { it.element }
            .map(updater::getWritable)
            .mapNotNull(KtTypeReference::getTypeElementWithoutQuestionMark)

        writableTypeReferences.forEach { it.replace(bound) }

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

    override fun getFamilyName(): String = KotlinBundle.message("inline.type.parameter")
}

data class InlineTypeParameterContext(
    val parameter: KtTypeParameter,
    val bound: KtTypeReference,
    val constraint: KtElement?,
)

fun prepareInlineTypeParameterContext(
    element: KtTypeReference,
    parameterList: KtTypeParameterList,
): InlineTypeParameterContext? {
    return when (val parent = element.parent) {
        is KtTypeParameter -> {
            val bound = parent.extendsBound ?: return null
            InlineTypeParameterContext(parent, bound, constraint = null)
        }

        is KtTypeConstraint -> {
            val subjectTypeParameterName = parent.subjectTypeParameterName?.text ?: return null
            val parameter = parameterList.parameters.firstOrNull { it.name == subjectTypeParameterName } ?: return null
            val bound = parent.boundTypeReference ?: return null
            InlineTypeParameterContext(parameter, bound, constraint = parent)
        }

        else -> null
    }
}

private fun KtTypeReference.getTypeElementWithoutQuestionMark(): KtTypeElement? =
    (typeElement as? KtNullableType)?.innerType ?: typeElement
