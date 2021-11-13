// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.idea.intentions.ConvertToIndexedFunctionCallIntention.Companion.nonIndexedFunctions
import org.jetbrains.kotlin.idea.intentions.appendCallOrQualifiedExpression
import org.jetbrains.kotlin.idea.intentions.collectLabeledReturnExpressions
import org.jetbrains.kotlin.idea.intentions.setLabel
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi2ir.deparenthesize
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class ConvertToNonIndexedFunctionCallFix(
    element: KtFunctionLiteral,
    private val nonIndexedFunctionName: String,
    private val labeledReturnExpressions: List<SmartPsiElementPointer<KtReturnExpression>>
) : KotlinQuickFixAction<KtFunctionLiteral>(element) {
    override fun getFamilyName() = KotlinBundle.message("convert.to.0", "'$nonIndexedFunctionName'")

    override fun getText() = familyName

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val functionLiteral = this.element ?: return
        val parameterList = functionLiteral.valueParameterList ?: return
        val call = functionLiteral.getStrictParentOfType<KtCallExpression>() ?: return

        labeledReturnExpressions.forEach { it.element?.setLabel(nonIndexedFunctionName) }
        parameterList.removeParameter(0)
        val psiFactory = KtPsiFactory(functionLiteral)
        val callOrQualified = call.getQualifiedExpressionForSelector() ?: call
        callOrQualified.replace(
            psiFactory.buildExpression {
                appendCallOrQualifiedExpression(call, nonIndexedFunctionName)
            }
        )
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val parameter = diagnostic.psiElement as? KtParameter ?: return null
            val parameterList = parameter.parent as? KtParameterList ?: return null
            val parameters = parameterList.parameters
            if (parameters.size < 2 || parameters.first() != parameter) return null

            val functionLiteral = parameterList.parent as? KtFunctionLiteral ?: return null
            val call = functionLiteral.parentCallExpression() ?: return null
            val functionName = call.calleeExpression?.text ?: return null
            val (nonIndexedFunctionFqName, nonIndexedFunctionName) = nonIndexedFunctions[functionName] ?: return null
            val context = functionLiteral.analyze(BodyResolveMode.PARTIAL)
            if (call.getResolvedCall(context)?.isCalling(nonIndexedFunctionFqName) != true) return null

            val labeledReturnExpressions =
                functionLiteral.collectLabeledReturnExpressions(functionName, context).map { it.createSmartPointer() }
            return ConvertToNonIndexedFunctionCallFix(functionLiteral, nonIndexedFunctionName, labeledReturnExpressions)
        }

        private fun KtFunctionLiteral.parentCallExpression(): KtCallExpression? {
            val lambda = parent as? KtLambdaExpression ?: return null
            val argument = lambda.getStrictParentOfType<KtValueArgument>()?.takeIf {
                it.getArgumentExpression()?.deparenthesize() == lambda
            } ?: return null
            return argument.getStrictParentOfType()
        }
    }
}
