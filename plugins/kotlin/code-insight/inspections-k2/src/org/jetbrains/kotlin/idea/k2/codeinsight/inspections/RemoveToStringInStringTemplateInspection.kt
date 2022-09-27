// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.canPlaceAfterSimpleNameEntry
import org.jetbrains.kotlin.util.OperatorNameConventions

class RemoveToStringInStringTemplateInspection :
    AbstractKotlinApplicatorBasedInspection<KtDotQualifiedExpression, KotlinApplicatorInput.Empty>(KtDotQualifiedExpression::class),
    CleanupLocalInspectionTool {
    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtDotQualifiedExpression> =
        applicabilityRanges { dotQualifiedExpression: KtDotQualifiedExpression ->
            val selectorExpression = dotQualifiedExpression.selectorExpression ?: return@applicabilityRanges emptyList()
            listOf(selectorExpression.textRange.shiftLeft(dotQualifiedExpression.startOffset))
        }

    override fun getInputProvider(): KotlinApplicatorInputProvider<KtDotQualifiedExpression, KotlinApplicatorInput.Empty> =
        inputProvider { dotQualifiedExpression: KtDotQualifiedExpression ->
            val call = dotQualifiedExpression.resolveCall()?.successfulFunctionCallOrNull() ?: return@inputProvider null
            val allOverriddenSymbols = listOf(call.symbol) + call.symbol.getAllOverriddenSymbols()
            val toStringFqName = FqName("kotlin.Any.toString")
            if (allOverriddenSymbols.any { it.callableIdIfNonLocal?.asSingleFqName() == toStringFqName }) {
                KotlinApplicatorInput.Empty
            } else {
                null
            }
        }

    override fun getApplicator(): KotlinApplicator<KtDotQualifiedExpression, KotlinApplicatorInput.Empty> = applicator {
        familyAndActionName(KotlinBundle.lazyMessage("remove.to.string.fix.text"))
        isApplicableByPsi isApplicable@{ dotQualifiedExpression ->
            if (dotQualifiedExpression.parent !is KtBlockStringTemplateEntry) return@isApplicable false
            if (dotQualifiedExpression.receiverExpression is KtSuperExpression) return@isApplicable false
            val callExpression = dotQualifiedExpression.selectorExpression as? KtCallExpression ?: return@isApplicable false
            val referenceExpression = callExpression.calleeExpression as? KtNameReferenceExpression ?: return@isApplicable false
            referenceExpression.getReferencedNameAsName() == OperatorNameConventions.TO_STRING && callExpression.valueArguments.isEmpty()
        }
        applyTo { dotQualifiedExpression, _ ->
            val receiverExpression = dotQualifiedExpression.receiverExpression
            val templateEntry = dotQualifiedExpression.parent as? KtBlockStringTemplateEntry
            if (receiverExpression is KtNameReferenceExpression &&
                templateEntry != null &&
                canPlaceAfterSimpleNameEntry(templateEntry.nextSibling)
            ) {
                val factory = KtPsiFactory(templateEntry)
                templateEntry.replace(factory.createSimpleNameStringTemplateEntry(receiverExpression.getReferencedName()))
            } else {
                dotQualifiedExpression.replace(receiverExpression)
            }
        }
    }
}

