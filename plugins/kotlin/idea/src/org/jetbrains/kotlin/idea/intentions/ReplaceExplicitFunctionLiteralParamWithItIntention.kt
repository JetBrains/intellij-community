// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ParameterDescriptor
import org.jetbrains.kotlin.descriptors.impl.AnonymousFunctionDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAsReplacement
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.ReplaceExplicitLambdaParameterWithItUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.ReplaceExplicitLambdaParameterWithItUtils.ParamRenamingProcessor
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class ReplaceExplicitFunctionLiteralParamWithItIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName() = KotlinBundle.message("replace.explicit.lambda.parameter.with.it")

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        val functionLiteral = targetFunctionLiteral(element, editor.caretModel.offset) ?: return false
        val explicitParameterName = functionLiteral.valueParameters.singleOrNull()?.name ?: return false
        val lambda = ReplaceExplicitLambdaParameterWithItUtils.getLambda(element, functionLiteral) ?: return false
        val call = lambda.getStrictParentOfType<KtCallExpression>()
        if (call != null) {
            val argumentIndex = call.valueArguments.indexOfFirst { it.getArgumentExpression() == lambda }
            val callOrQualified = call.getQualifiedExpressionForSelectorOrThis()
            val analyzableExpression = ReplaceExplicitLambdaParameterWithItUtils.createAnalyzableExpression(project, argumentIndex, callOrQualified)
                                       ?: return false
            val newContext = analyzableExpression.analyzeAsReplacement(callOrQualified, callOrQualified.analyze(BodyResolveMode.PARTIAL))
            if (analyzableExpression.getResolvedCall(newContext)?.resultingDescriptor == null) return false
        }

        text = KotlinBundle.message("replace.explicit.parameter.0.with.it", explicitParameterName)
        return true
    }

    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val caretOffset = editor.caretModel.offset
        val functionLiteral = targetFunctionLiteral(element, editor.caretModel.offset) ?: return
        val cursorInParameterList = functionLiteral.valueParameterList?.textRange?.containsOffset(caretOffset) ?: return
        ParamRenamingProcessor(editor, functionLiteral, cursorInParameterList).run()
    }

    private fun targetFunctionLiteral(element: PsiElement, caretOffset: Int): KtFunctionLiteral? {
        val expression = element.getParentOfType<KtNameReferenceExpression>(true)
        if (expression != null) {
            val target = expression.resolveMainReferenceToDescriptors().singleOrNull() as? ParameterDescriptor ?: return null
            val functionDescriptor = target.containingDeclaration as? AnonymousFunctionDescriptor ?: return null
            return DescriptorToSourceUtils.descriptorToDeclaration(functionDescriptor) as? KtFunctionLiteral
        }

        val functionLiteral = element.getParentOfType<KtFunctionLiteral>(true) ?: return null
        val arrow = functionLiteral.arrow ?: return null
        if (caretOffset > arrow.endOffset) return null
        return functionLiteral
    }
}
