// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.conventionNameCalls

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.ReplaceGetOrSetInspectionUtils
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.intentions.calleeName
import org.jetbrains.kotlin.idea.intentions.isReceiverExpressionWithValue
import org.jetbrains.kotlin.idea.intentions.toResolvedCall
import org.jetbrains.kotlin.idea.util.calleeTextRangeInThis
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.model.isReallySuccess
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.util.isValidOperator

class ReplaceGetOrSetInspection : AbstractApplicabilityBasedInspection<KtDotQualifiedExpression>(
    KtDotQualifiedExpression::class.java
) {
    private fun FunctionDescriptor.isExplicitOperator(): Boolean {
        return if (overriddenDescriptors.isEmpty())
            containingDeclaration !is JavaClassDescriptor && isOperator
        else
            overriddenDescriptors.any { it.isExplicitOperator() }
    }

    override fun isApplicable(element: KtDotQualifiedExpression): Boolean {
        if (!ReplaceGetOrSetInspectionUtils.looksLikeGetOrSetOperatorCall(element)) return false

        val callExpression = element.callExpression ?: return false
        val bindingContext = callExpression.analyze(BodyResolveMode.PARTIAL_WITH_CFA)
        val resolvedCall = callExpression.getResolvedCall(bindingContext) ?: return false
        if (!resolvedCall.isReallySuccess()) return false

        val target = resolvedCall.resultingDescriptor as? FunctionDescriptor ?: return false
        if (!target.isValidOperator() || target.name !in setOf(OperatorNameConventions.GET, OperatorNameConventions.SET)) return false

        if (!element.isReceiverExpressionWithValue()) return false

        return target.name != OperatorNameConventions.SET || !element.isUsedAsExpression(bindingContext)
    }

    override fun inspectionText(element: KtDotQualifiedExpression) = KotlinBundle.message("should.be.replaced.with.indexing")

    override fun inspectionHighlightType(element: KtDotQualifiedExpression): ProblemHighlightType =
        if ((element.toResolvedCall(BodyResolveMode.PARTIAL)?.resultingDescriptor as? FunctionDescriptor)?.isExplicitOperator() == true) {
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        } else {
            ProblemHighlightType.INFORMATION
        }

    override val defaultFixText: String get() = KotlinBundle.message("replace.get.or.set.call.with.indexing.operator")

    override fun fixText(element: KtDotQualifiedExpression): String {
        val callExpression = element.callExpression ?: return defaultFixText
        val resolvedCall = callExpression.resolveToCall() ?: return defaultFixText
        return KotlinBundle.message("replace.0.call.with.indexing.operator", resolvedCall.resultingDescriptor.name.asString())
    }

    override fun inspectionHighlightRangeInElement(element: KtDotQualifiedExpression) = element.calleeTextRangeInThis()

    override fun applyTo(element: KtDotQualifiedExpression, project: Project, editor: Editor?) {
        ReplaceGetOrSetInspectionUtils.replaceGetOrSetWithPropertyAccessor(
            element,
            element.calleeName == OperatorNameConventions.SET.identifier,
            editor?.let { e -> { e.caretModel.moveToOffset(it) } }
        )
    }
}
