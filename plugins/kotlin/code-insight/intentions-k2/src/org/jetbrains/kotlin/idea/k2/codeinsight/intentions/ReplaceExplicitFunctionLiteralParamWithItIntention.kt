// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.Utils.computeWithProgressIcon
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.application
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.ReplaceExplicitLambdaParameterWithItUtils.ParamRenamingProcessor
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.ReplaceExplicitLambdaParameterWithItUtils.createAnalyzableExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.ReplaceExplicitLambdaParameterWithItUtils.getLambda
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*


internal class ReplaceExplicitFunctionLiteralParamWithItIntention : SelfTargetingIntention<KtElement>(
    KtElement::class.java,
    KotlinBundle.lazyMessage("replace.explicit.lambda.parameter.with.it"),
) {

    override fun startInWriteAction(): Boolean = false

    override fun isApplicableTo(element: KtElement, caretOffset: Int): Boolean {
        val functionLiteral = targetFunctionLiteral(element, caretOffset) ?: return false
        val explicitParameterName = functionLiteral.valueParameters.singleOrNull()?.name ?: return false
        val lambda = getLambda(element, functionLiteral) ?: return false
        val call = lambda.getStrictParentOfType<KtCallExpression>()

        if (call != null) {
            val argumentIndex = call.valueArguments.indexOfFirst { it.getArgumentExpression() == lambda }
            val callOrQualified = call.getQualifiedExpressionForSelectorOrThis()
            val newCallOrQualified = createAnalyzableExpression(element.project, argumentIndex, callOrQualified) ?: return false
            val codeFragment = KtPsiFactory(element.project).createExpressionCodeFragment(newCallOrQualified.text, element)
            val contentElement = codeFragment.getContentElement()!!

            return computeWithProgressIconIfNeeded(element.findExistingEditor()!!, caretOffset) {
                analyze(contentElement) {
                    val resolveToCall = contentElement.getPossiblyQualifiedCallExpression()?.resolveToCall()
                    resolveToCall?.singleFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol != null
                }
            }
        }

        setTextGetter { KotlinBundle.message("replace.explicit.parameter.0.with.it", explicitParameterName) }
        return true
    }

    override fun applyTo(element: KtElement, editor: Editor?) {
        val caretOffset = editor?.caretModel?.offset ?: return
        val functionLiteral = targetFunctionLiteral(element, editor.caretModel.offset, editor) ?: return
        val cursorInParameterList = functionLiteral.valueParameterList?.textRange?.containsOffset(caretOffset) ?: return
        ParamRenamingProcessor(editor, functionLiteral, cursorInParameterList).run()
    }

}

private fun targetFunctionLiteral(element: KtElement, caretOffset: Int, editor: Editor? = null): KtFunctionLiteral? {
    val expression = element.getParentOfType<KtNameReferenceExpression>(false)
    if (expression != null) {
        val existingEditor = editor ?: element.findExistingEditor() ?: return null
        return computeWithProgressIconIfNeeded(existingEditor, caretOffset) {
            analyze(expression) {
                val target = expression.mainReference.resolveToSymbols().singleOrNull() as? KaValueParameterSymbol ?: return@analyze null
                val functionDescriptor = target.containingSymbol as? KaAnonymousFunctionSymbol ?: return@analyze null
                functionDescriptor.psi as? KtFunctionLiteral
            }
        }
    }

    val functionLiteral = element.getParentOfType<KtFunctionLiteral>(true) ?: return null
    val arrow = functionLiteral.arrow ?: return null
    if (caretOffset > arrow.endOffset) return null
    return functionLiteral
}

private fun <T> computeWithProgressIconIfNeeded(editor: Editor, caretOffset: Int, action: () -> T): T {
    return if (application.isDispatchThread()) {
        val aComponent = editor.contentComponent
        val point = RelativePoint(aComponent, editor.logicalPositionToXY(editor.offsetToLogicalPosition(caretOffset)))
        computeWithProgressIcon(point, aComponent, ActionPlaces.UNKNOWN) {
            readAction { action() }
        }
    }
    else {
        action()
    }
}
