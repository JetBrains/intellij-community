// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.slicer

import com.intellij.psi.PsiCall
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.slicer.SliceUsage
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitInvokeCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.idea.base.analysis.api.utils.defaultValue
import org.jetbrains.kotlin.idea.codeInsight.slicer.KotlinSliceAnalysisMode
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch

data class ArgumentSliceProducer(
    private val parameterIndex: Int,
    private val isExtension: Boolean
) : SliceProducer {
    override fun produce(usage: UsageInfo, mode: KotlinSliceAnalysisMode, parent: SliceUsage): Collection<SliceUsage> {
        val element = usage.element ?: return emptyList()
        val argumentExpression = extractArgumentExpression(element) ?: return emptyList()
        return listOf(KotlinSliceUsage(argumentExpression, parent, mode, forcedExpressionMode = true))
    }

    override val testPresentation: String = "ARGUMENT #$parameterIndex".let { if (isExtension) "$it EXTENSION" else it }

    @OptIn(KaExperimentalApi::class)
    private fun extractArgumentExpression(refElement: PsiElement): PsiElement? {
        val refParent = refElement.parent
        return when {
            refElement is KtExpression -> {
                val callElement = refElement as? KtCallElement
                    ?: refElement.getParentOfTypeAndBranch { calleeExpression }
                    ?: (refParent as? KtDotQualifiedExpression)?.selectorExpression as? KtCallElement
                    ?: return null
                analyze(callElement) {
                    val callInfo = callElement.resolveSuccessfulCall() ?: return null

                    val parameterIndexToUse = parameterIndex + (if (isExtension && (callInfo is KaImplicitInvokeCall)) 1 else 0)

                    val variableSignature = callInfo.signature.valueParameters[parameterIndexToUse]

                    callInfo.valueArgumentMapping.entries.firstOrNull { (_, v) -> v == variableSignature }?.key ?: variableSignature.symbol.defaultValue
                }
            }

            refParent is PsiCall -> refParent.argumentList?.expressions?.getOrNull(parameterIndex + (if (isExtension) 1 else 0))

            refElement is PsiMethod -> refElement.parameterList.parameters.getOrNull(parameterIndex + (if (isExtension) 1 else 0))

            else -> null
        }
    }
}
