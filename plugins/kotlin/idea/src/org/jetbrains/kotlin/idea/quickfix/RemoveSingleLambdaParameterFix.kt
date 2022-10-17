// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RemoveSingleLambdaParameterFix(element: KtParameter) : KotlinQuickFixAction<KtParameter>(element) {
    override fun getFamilyName() = KotlinBundle.message("remove.single.lambda.parameter.declaration")
    override fun getText() = familyName

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val parameterList = element.parent as? KtParameterList ?: return
        val ownerFunction = parameterList.ownerFunction ?: return
        val arrow = ownerFunction.node.findChildByType(KtTokens.ARROW) ?: return

        parameterList.delete()
        ownerFunction.node.removeChild(arrow)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val parameter = diagnostic.psiElement as? KtParameter ?: return null
            val parameterList = parameter.parent as? KtParameterList ?: return null

            if (parameterList.parameters.size != 1) return null

            val lambda = parameterList.parent.parent as? KtLambdaExpression ?: return null

            val lambdaParent = lambda.parent
            if (lambdaParent is KtWhenEntry || lambdaParent is KtContainerNodeForControlStructureBody) return null

            if (lambda.getQualifiedExpressionForReceiver() != null) return null

            val property = lambda.getStrictParentOfType<KtProperty>()
            if (property != null && property.typeReference == null) return null

            return RemoveSingleLambdaParameterFix(parameter)
        }
    }
}