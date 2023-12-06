// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.idea.intentions.ReplaceItWithExplicitFunctionLiteralParamIntention
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor

private val scopeFunctions: List<FqName> = listOf(
    "kotlin.also",
    "kotlin.let",
    "kotlin.takeIf",
    "kotlin.takeUnless"
).map { FqName(it) }

class NestedLambdaShadowedImplicitParameterInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return lambdaExpressionVisitor(fun(lambda: KtLambdaExpression) {
            if (lambda.valueParameters.isNotEmpty()) return
            if (lambda.getStrictParentOfType<KtLambdaExpression>() == null) return

            val context = lambda.safeAnalyzeNonSourceRootCode()
            val implicitParameter = lambda.getImplicitParameter(context) ?: return
            if (lambda.getParentImplicitParameterLambda(context) == null) return

            val qualifiedExpression = lambda.getStrictParentOfType<KtQualifiedExpression>()
            if (qualifiedExpression != null) {
                val receiver = qualifiedExpression.receiverExpression
                val call = qualifiedExpression.callExpression
                if (receiver.text == StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier && call?.isCalling(scopeFunctions, context) == true) return
            }

            val containingFile = lambda.containingFile
            lambda.forEachDescendantOfType<KtNameReferenceExpression> {
                if (it.isImplicitParameterReference(lambda, implicitParameter, context)) {
                    holder.registerProblem(
                        it,
                        KotlinBundle.message("implicit.parameter.it.of.enclosing.lambda.is.shadowed"),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        AddExplicitParameterToOuterLambdaFix(),
                        IntentionWrapper(ReplaceItWithExplicitFunctionLiteralParamIntention())
                    )
                }
            }
        })
    }

    private class AddExplicitParameterToOuterLambdaFix : LocalQuickFix {
        override fun getName() = KotlinBundle.message("add.explicit.parameter.to.outer.lambda.fix.text")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val implicitParameterReference = descriptor.psiElement as? KtNameReferenceExpression ?: return
            val lambda = implicitParameterReference.getStrictParentOfType<KtLambdaExpression>() ?: return
            val parentLambda = lambda.getParentImplicitParameterLambda() ?: return
            val parameter = parentLambda.functionLiteral.getOrCreateParameterList().addParameterBefore(
                KtPsiFactory(project).createLambdaParameterList(StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier).parameters.first(), null
            )
            val editor = parentLambda.findExistingEditor() ?: return
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
            editor.caretModel.moveToOffset(parameter.startOffset)
            KotlinVariableInplaceRenameHandler().doRename(parameter, editor, null)
        }
    }
}

private fun KtLambdaExpression.getImplicitParameter(context: BindingContext): ValueParameterDescriptor? {
    return context[BindingContext.FUNCTION, functionLiteral]?.valueParameters?.singleOrNull()
}

private fun KtLambdaExpression.getParentImplicitParameterLambda(context: BindingContext = this.analyze()): KtLambdaExpression? {
    return getParentOfTypesAndPredicate(true, KtLambdaExpression::class.java) { lambda ->
        if (lambda.valueParameters.isNotEmpty()) return@getParentOfTypesAndPredicate false
        val implicitParameter = lambda.getImplicitParameter(context) ?: return@getParentOfTypesAndPredicate false
        lambda.anyDescendantOfType<KtNameReferenceExpression> {
            it.isImplicitParameterReference(lambda, implicitParameter, context)
        }
    }
}

private fun KtNameReferenceExpression.isImplicitParameterReference(
    lambda: KtLambdaExpression,
    implicitParameter: ValueParameterDescriptor,
    context: BindingContext
): Boolean {
    return text == StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
            && getStrictParentOfType<KtLambdaExpression>() == lambda
            && getResolvedCall(context)?.resultingDescriptor == implicitParameter
}