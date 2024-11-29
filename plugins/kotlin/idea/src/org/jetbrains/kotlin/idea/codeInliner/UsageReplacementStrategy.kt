// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInliner

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.compareDescriptors
import org.jetbrains.kotlin.idea.intentions.ConvertReferenceToLambdaIntention
import org.jetbrains.kotlin.idea.intentions.SpecifyExplicitLambdaSignatureIntention
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


fun unwrapSpecialUsageOrNull(
    usage: KtReferenceExpression
): KtSimpleNameExpression? {
    if (usage !is KtSimpleNameExpression) return null

    when (val usageParent = usage.parent) {
        is KtCallableReferenceExpression -> {
            if (usageParent.callableReference != usage) return null
            val (name, descriptor) = usage.nameAndDescriptor
            return ConvertReferenceToLambdaIntention.Holder.applyTo(usageParent)?.let {
                findNewUsage(it, name, descriptor)
            }
        }

        is KtCallElement -> {
            for (valueArgument in usageParent.valueArguments.asReversed()) {
                val callableReferenceExpression = valueArgument.getArgumentExpression() as? KtCallableReferenceExpression ?: continue
                ConvertReferenceToLambdaIntention.Holder.applyTo(callableReferenceExpression)
            }

            val lambdaExpressions = usageParent.valueArguments.mapNotNull { it.getArgumentExpression() as? KtLambdaExpression }
            if (lambdaExpressions.isEmpty()) return null

            val (name, descriptor) = usage.nameAndDescriptor
            val grandParent = usageParent.parent
            for (lambdaExpression in lambdaExpressions) {
                val functionDescriptor = lambdaExpression.functionLiteral.resolveToDescriptorIfAny() as? FunctionDescriptor ?: continue
                if (functionDescriptor.valueParameters.isNotEmpty()) {
                    SpecifyExplicitLambdaSignatureIntention.Holder.applyTo(lambdaExpression)
                }
            }

            return grandParent.safeAs<KtElement>()?.let {
                findNewUsage(it, name, descriptor)
            }
        }

    }

    return null
}

private val KtSimpleNameExpression.nameAndDescriptor get() = getReferencedName() to resolveToCall()?.candidateDescriptor

private fun findNewUsage(
    element: KtElement,
    targetName: String?,
    targetDescriptor: DeclarationDescriptor?
): KtSimpleNameExpression? = element.findDescendantOfType {
    it.getReferencedName() == targetName && compareDescriptors(it.project, targetDescriptor, it.resolveToCall()?.candidateDescriptor)
}
