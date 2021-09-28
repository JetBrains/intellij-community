// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.analysis.analyzeAsReplacement
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.util.calleeTextRangeInThis
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.util.getExplicitReceiverValue
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.isSubclassOf

class ReplacePutWithAssignmentInspection : AbstractApplicabilityBasedInspection<KtDotQualifiedExpression>(
    KtDotQualifiedExpression::class.java
) {

    override fun isApplicable(element: KtDotQualifiedExpression): Boolean {
        if (element.receiverExpression is KtSuperExpression) return false

        val callExpression = element.callExpression
        if (callExpression?.valueArguments?.size != 2) return false

        val calleeExpression = callExpression.calleeExpression as? KtNameReferenceExpression ?: return false
        if (calleeExpression.getReferencedName() !in compatibleNames) return false

        val context = element.analyze()
        if (element.isUsedAsExpression(context)) return false

        // This fragment had to be added because of incorrect behaviour of isUsesAsExpression
        // TODO: remove it after fix of KT-25682
        val binaryExpression = element.getStrictParentOfType<KtBinaryExpression>()
        val right = binaryExpression?.right
        if (binaryExpression?.operationToken == KtTokens.ELVIS &&
            right != null && (right == element || KtPsiUtil.deparenthesize(right) == element)
        ) return false

        val resolvedCall = element.getResolvedCall(context)
        val receiverType = resolvedCall?.getExplicitReceiverValue()?.type ?: return false
        val receiverClass = receiverType.constructor.declarationDescriptor as? ClassDescriptor ?: return false
        if (!receiverClass.isSubclassOf(DefaultBuiltIns.Instance.mutableMap)) return false

        val assignment = createAssignmentExpression(element) ?: return false
        val newContext = assignment.analyzeAsReplacement(element, context)
        return assignment.left.getResolvedCall(newContext)?.resultingDescriptor?.fqNameOrNull() == FqName("kotlin.collections.set")
    }

    override fun applyTo(element: KtDotQualifiedExpression, project: Project, editor: Editor?) {
        val assignment = createAssignmentExpression(element) ?: return
        element.replace(assignment)
    }

    private fun createAssignmentExpression(element: KtDotQualifiedExpression): KtBinaryExpression? {
        val valueArguments = element.callExpression?.valueArguments ?: return null
        val firstArg = valueArguments[0]?.getArgumentExpression() ?: return null
        val secondArg = valueArguments[1]?.getArgumentExpression() ?: return null
        val label = if (secondArg is KtLambdaExpression) {
            val returnLabel = secondArg.findDescendantOfType<KtReturnExpression>()?.getLabelName()
            compatibleNames.firstOrNull { it == returnLabel }?.plus("@") ?: ""
        } else ""
        return KtPsiFactory(element).createExpressionByPattern(
            "$0[$1] = $label$2",
            element.receiverExpression,
            firstArg,
            secondArg
        ) as? KtBinaryExpression
    }

    override fun inspectionHighlightRangeInElement(element: KtDotQualifiedExpression) = element.calleeTextRangeInThis()

    override fun inspectionText(element: KtDotQualifiedExpression): String =
        KotlinBundle.message("map.put.should.be.converted.to.assignment")

    override val defaultFixText get() = KotlinBundle.message("convert.put.to.assignment")

    companion object {
        private val compatibleNames = setOf("put")
    }
}