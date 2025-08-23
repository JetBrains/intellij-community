// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.resolve.BindingContextUtils
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getExplicitReceiverValue
import org.jetbrains.kotlin.resolve.descriptorUtil.isSubclassOf
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class ReplaceAddWithPlusAssignIntention : SelfTargetingOffsetIndependentIntention<KtDotQualifiedExpression>(
    KtDotQualifiedExpression::class.java,
    KotlinBundle.messagePointer("replace.with1")
) {
    @SafeFieldForPreview
    private val compatibleNames = setOf("add", "addAll")

    override fun isApplicableTo(element: KtDotQualifiedExpression): Boolean {
        if (element.callExpression?.valueArguments?.size != 1) return false

        if (element.calleeName !in compatibleNames) return false
        setTextGetter(KotlinBundle.messagePointer("replace.0.with", element.calleeName.toString()))

        val context = element.analyze(BodyResolveMode.PARTIAL)
        BindingContextUtils.extractVariableDescriptorFromReference(context, element.receiverExpression)?.let {
            if (it.isVar) return false
        } ?: return false

        val resolvedCall = element.getResolvedCall(context) ?: return false
        val receiverType = resolvedCall.getExplicitReceiverValue()?.type ?: return false
        val receiverClass = receiverType.constructor.declarationDescriptor as? ClassDescriptor ?: return false
        return receiverClass.isSubclassOf(DefaultBuiltIns.Instance.mutableCollection)
    }

    override fun applyTo(element: KtDotQualifiedExpression, editor: Editor?) {
        element.replace(
            KtPsiFactory(element.project).createExpressionByPattern(
                "$0 += $1", element.receiverExpression,
                element.callExpression?.valueArguments?.get(0)?.getArgumentExpression() ?: return
            )
        )
    }
}