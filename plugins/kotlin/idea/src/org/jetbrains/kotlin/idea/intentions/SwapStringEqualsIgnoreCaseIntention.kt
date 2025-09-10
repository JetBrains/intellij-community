// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull

class SwapStringEqualsIgnoreCaseIntention : SelfTargetingRangeIntention<KtDotQualifiedExpression>(
    KtDotQualifiedExpression::class.java,
    KotlinBundle.messagePointer("flip.equals")
), LowPriorityAction {

    override fun applicabilityRange(element: KtDotQualifiedExpression): TextRange? {
        val callExpression = element.callExpression ?: return null
        if (callExpression.calleeExpression?.text != equalsFunctionShortName) return null
        if (callExpression.valueArguments.mapNotNull { it.getArgumentExpression() }.size != 2) return null

        val descriptor = element.getCallableDescriptor() ?: return null
        if (descriptor.fqNameOrNull() != equalsFunctionFqName) return null
        val valueParameters = descriptor.valueParameters.takeIf { it.size == 2 } ?: return null
        if (!KotlinBuiltIns.isStringOrNullableString(valueParameters[0].type)) return null
        if (!KotlinBuiltIns.isBoolean(valueParameters[1].type)) return null

        return element.callExpression?.calleeExpression?.textRange
    }

    override fun applyTo(element: KtDotQualifiedExpression, editor: Editor?) {
        val callExpression = element.callExpression ?: return
        val valueArguments = callExpression.valueArguments
        val offset = (editor?.caretModel?.offset ?: 0) - (callExpression.calleeExpression?.startOffset ?: 0)
        val receiverExpression = element.receiverExpression
        val newElement = KtPsiFactory(element.project).createExpressionByPattern(
            "$0.equals($1, $2)",
            valueArguments[0].getArgumentExpression()!!,
            receiverExpression,
            valueArguments[1].text
        )

        val replacedElement = element.replaced(newElement) as? KtDotQualifiedExpression
        replacedElement?.callExpression?.calleeExpression?.startOffset?.let {
            editor?.moveCaret(it + offset)
        }
    }
}

private val equalsFunctionFqName: FqName = FqName("kotlin.text.equals")

private val equalsFunctionShortName: String = equalsFunctionFqName.shortName().asString()