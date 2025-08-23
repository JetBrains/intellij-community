// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.PairProcessor
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.convertToIfNotNullExpression
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.introduceValueForCondition
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isStableSimpleExpression
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsStatement
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class SafeAccessToIfThenIntention : SelfTargetingRangeIntention<KtSafeQualifiedExpression>(
    KtSafeQualifiedExpression::class.java,
    KotlinBundle.messagePointer("replace.safe.access.expression.with.if.expression")
), LowPriorityAction {
    override fun applicabilityRange(element: KtSafeQualifiedExpression): TextRange? {
        if (element.selectorExpression == null) return null
        return element.operationTokenNode.textRange
    }

    override fun applyTo(element: KtSafeQualifiedExpression, editor: Editor?) {
        val receiver = KtPsiUtil.safeDeparenthesize(element.receiverExpression)
        val selector = element.selectorExpression!!

        val receiverIsStable = receiver.isStableSimpleExpression()

        val psiFactory = KtPsiFactory(element.project)
        val dotQualified = psiFactory.createExpressionByPattern("$0.$1", receiver, selector)

        val elseClause = if (element.isUsedAsStatement(element.analyze())) null else psiFactory.createExpression("null")
        var ifExpression = element.convertToIfNotNullExpression(receiver, dotQualified, elseClause)

        var isAssignment = false
        val binaryExpression = (ifExpression.parent as? KtParenthesizedExpression)?.parent as? KtBinaryExpression
        val right = binaryExpression?.right
        if (right != null && binaryExpression.operationToken == KtTokens.EQ) {
            val replaced = binaryExpression.replaced(psiFactory.createExpressionByPattern("$0 = $1", ifExpression.text, right))
            ifExpression = replaced.findDescendantOfType()!!
            isAssignment = true
        }

        val isRedundantLetCallRemoved = ifExpression.removeRedundantLetCallIfPossible(editor)

        if (!receiverIsStable) {
            val valueToExtract = when {
                isAssignment ->
                    ((ifExpression.then as? KtBinaryExpression)?.left as? KtDotQualifiedExpression)?.receiverExpression
                isRedundantLetCallRemoved -> {
                    val context = ifExpression.analyze(BodyResolveMode.PARTIAL)
                    val descriptor = (ifExpression.condition as? KtBinaryExpression)?.left?.getResolvedCall(context)?.resultingDescriptor
                    ifExpression.then?.findDescendantOfType<KtNameReferenceExpression> {
                        it.getReferencedNameAsName() == descriptor?.name && it.getResolvedCall(context)?.resultingDescriptor == descriptor
                    }
                }
                else ->
                    (ifExpression.then as? KtDotQualifiedExpression)?.receiverExpression
            }
            if (valueToExtract != null) ifExpression.introduceValueForCondition(valueToExtract, editor)
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    private fun KtIfExpression.removeRedundantLetCallIfPossible(editor: Editor?): Boolean = allowAnalysisOnEdt {
        return allowAnalysisFromWriteAction {
            val callExpression = (then as? KtQualifiedExpression)?.callExpression ?: return@allowAnalysisFromWriteAction false
            if (callExpression.calleeExpression?.text != "let") return@allowAnalysisFromWriteAction false

            editor?.document?.let {
                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(it)
            }

            val toolWrappers = LocalInspectionEP.LOCAL_INSPECTION
                .point
                .extensionList
                .asSequence()
                .filter { it.shortName == "ComplexRedundantLet" }
                .map { LocalInspectionToolWrapper(it) }
                .toList()

            val wrappers = InspectionEngine.inspectEx(
                /* toolWrappers = */ toolWrappers,
                /* psiFile = */ callExpression.containingKtFile,
                /* restrictRange = */ callExpression.getTextRange(),
                /* priorityRange = */ callExpression.getTextRange(),
                /* isOnTheFly = */ false,
                /* inspectInjectedPsi = */ false,
                /* ignoreSuppressedElements = */ false,
                /* indicator = */ ProgressManager.getInstance().progressIndicator ?: DaemonProgressIndicator(),
                /* foundDescriptorCallback = */ PairProcessor.alwaysTrue(),
            )

            val (_, problems) = wrappers.entries
                                    .singleOrNull()
                                ?: return@allowAnalysisFromWriteAction false

            val problemDescriptor = problems.singleOrNull()
                                    ?: return@allowAnalysisFromWriteAction false

            val quickFix = problemDescriptor.fixes
                               ?.singleOrNull()
                           ?: return false

            quickFix.applyFix(project, problemDescriptor)
            return@allowAnalysisFromWriteAction true
        }
    }
}
