// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.calls.util.getParameterForArgument
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class AddConversionCallFix(element: KtExpression, val targetType: String) : KotlinQuickFixAction<KtExpression>(element) {

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val expression = psiElement.safeAs<KtExpression>() ?: return emptyList()
            val targetType = calculateTargetType(expression) ?: return emptyList()
            return listOf(AddConversionCallFix(expression, targetType))
        }

        private fun calculateTargetType(expression: KtExpression): String? {
            val valueArgument = expression.getParentOfType<KtValueArgument>(false) ?: return null
            val callExpression = valueArgument.getParentOfType<KtCallExpression>(false) ?: return null
            val resolvedCall = callExpression.resolveToCall() ?: return null
            val parameterDescriptor = resolvedCall.getParameterForArgument(valueArgument) ?: return null
            val type = parameterDescriptor.type
            return IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(type)
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean = element != null

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val expression = element ?: return
        val convertExpression = KtPsiFactory(project).createExpressionByPattern("($0).to$1()", expression, targetType)
        expression.replace(convertExpression)
    }

    override fun getText(): String {
        return KotlinBundle.message("convert.expression.to.0", targetType)
    }

    override fun getFamilyName(): String = text
}