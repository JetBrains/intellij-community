// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType

object ReplaceExplicitLambdaParameterWithItUtils {

    class ParamRenamingProcessor(
        val editor: Editor,
        val functionLiteral: KtFunctionLiteral,
        private val cursorWasInParameterList: Boolean,
    ) : RenameProcessor(
        functionLiteral.project,
        functionLiteral.valueParameters.single(),
        StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier,
        false,
        false
    ) {
        override fun performRefactoring(usages: Array<out UsageInfo>) {
            super.performRefactoring(usages)

            functionLiteral.deleteChildRange(functionLiteral.valueParameterList, functionLiteral.arrow ?: return)

            if (cursorWasInParameterList) {
                editor.caretModel.moveToOffset(functionLiteral.bodyExpression?.textOffset ?: return)
            }

            val project = functionLiteral.project
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
            CodeStyleManager.getInstance(project).adjustLineIndent(functionLiteral.containingFile, functionLiteral.textRange)

        }
    }

    private fun KtFunctionLiteral.usesName(name: String): Boolean = anyDescendantOfType<KtSimpleNameExpression> { nameExpr ->
        nameExpr.getReferencedName() == name
    }

    fun getLambda(element: PsiElement, functionLiteral: KtFunctionLiteral): KtLambdaExpression? {
        val parameter = functionLiteral.valueParameters.singleOrNull() ?: return null
        if (parameter.typeReference != null) return null
        if (parameter.destructuringDeclaration != null) return null

        if (functionLiteral.anyDescendantOfType<KtFunctionLiteral> { literal ->
                literal.usesName(element.text) &&
                (!literal.hasParameterSpecification() || literal.usesName(StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier))
            }) return null

        val lambda = functionLiteral.parent as? KtLambdaExpression ?: return null
        val lambdaParent = lambda.parent
        if (lambdaParent is KtWhenEntry || lambdaParent is KtContainerNodeForControlStructureBody) return null
        return lambda
    }

    fun createAnalyzableExpression(project: Project, argumentIndex: Int, expression: KtExpression): KtExpression? {
        val copiedExpression = expression.copied()
        val newCall = (copiedExpression as? KtQualifiedExpression)?.callExpression
                      ?: copiedExpression as? KtCallExpression
                      ?: return null
        val newArgument = newCall.valueArguments.getOrNull(argumentIndex) ?: newCall.lambdaArguments.singleOrNull() ?: return null
        newArgument.replace(KtPsiFactory(project).createLambdaExpression("", "TODO()"))

        return copiedExpression
    }
}
