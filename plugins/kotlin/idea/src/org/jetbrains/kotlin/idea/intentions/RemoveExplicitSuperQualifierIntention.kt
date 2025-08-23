// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAsReplacement
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.error.ErrorUtils

@Suppress("DEPRECATION")
class RemoveExplicitSuperQualifierInspection : IntentionBasedInspection<KtSuperExpression>(
    RemoveExplicitSuperQualifierIntention::class
), CleanupLocalInspectionTool

class RemoveExplicitSuperQualifierIntention : SelfTargetingRangeIntention<KtSuperExpression>(
    KtSuperExpression::class.java,
    KotlinBundle.messagePointer("remove.explicit.supertype.qualification")
) {
    override fun applicabilityRange(element: KtSuperExpression): TextRange? {
        if (element.superTypeQualifier == null) return null

        val qualifiedExpression = element.getQualifiedExpressionForReceiver() ?: return null
        val selector = qualifiedExpression.selectorExpression ?: return null

        val bindingContext = selector.analyze(BodyResolveMode.PARTIAL_WITH_CFA)
        if (selector.getResolvedCall(bindingContext) == null) return null

        val newQualifiedExpression = KtPsiFactory(element.project).createExpressionByPattern(
            "$0.$1", toNonQualified(element, reformat = false), selector,
            reformat = false
        ) as KtQualifiedExpression

        val newBindingContext = newQualifiedExpression.analyzeAsReplacement(qualifiedExpression, bindingContext)
        val newResolvedCall = newQualifiedExpression.selectorExpression.getResolvedCall(newBindingContext) ?: return null
        if (ErrorUtils.isError(newResolvedCall.resultingDescriptor)) return null

        return TextRange(element.instanceReference.endOffset, element.labelQualifier?.startOffset ?: element.endOffset)
    }

    override fun applyTo(element: KtSuperExpression, editor: Editor?) {
        element.replace(toNonQualified(element, reformat = true))
    }

    private fun toNonQualified(superExpression: KtSuperExpression, reformat: Boolean): KtSuperExpression {
        val psiFactory = KtPsiFactory(superExpression.project)
        val labelName = superExpression.getLabelNameAsName()
        return (if (labelName != null)
            psiFactory.createExpressionByPattern("super@$0", labelName, reformat = reformat)
        else
            psiFactory.createExpression("super")) as KtSuperExpression
    }
}