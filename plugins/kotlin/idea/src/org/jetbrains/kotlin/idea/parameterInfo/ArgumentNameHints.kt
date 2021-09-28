// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.prevLeafs
import org.jetbrains.kotlin.builtins.extractParameterNameFromFunctionTypeArgument
import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.resolveCandidates
import org.jetbrains.kotlin.idea.intentions.AddNamesInCommentToJavaCallArgumentsIntention.Companion.toCommentedParameterName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.descriptors.JavaClassConstructorDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaMethodDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.calls.util.getCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.isAnnotationConstructor
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

fun provideArgumentNameHints(element: KtCallElement): List<InlayInfo> {
    if (element.valueArguments.none { it.getArgumentExpression()?.isUnclearExpression() == true }) return emptyList()
    val ctx = element.analyze(BodyResolveMode.PARTIAL)
    val call = element.getCall(ctx) ?: return emptyList()
    val resolvedCall = call.getResolvedCall(ctx)
    if (resolvedCall != null) {
        return getArgumentNameHintsForCallCandidate(resolvedCall, call.valueArgumentList)
    }
    val candidates = call.resolveCandidates(ctx, element.getResolutionFacade())
    if (candidates.isEmpty()) return emptyList()
    candidates.singleOrNull()?.let { return getArgumentNameHintsForCallCandidate(it, call.valueArgumentList) }
    return candidates.map { getArgumentNameHintsForCallCandidate(it, call.valueArgumentList) }.reduce { infos1, infos2 ->
        for (index in infos1.indices) {
            if (index >= infos2.size || infos1[index] != infos2[index]) {
                return@reduce infos1.subList(0, index)
            }
        }
        infos1
    }
}

private fun getArgumentNameHintsForCallCandidate(
    resolvedCall: ResolvedCall<out CallableDescriptor>,
    valueArgumentList: KtValueArgumentList?
): List<InlayInfo> {
    val resultingDescriptor = resolvedCall.resultingDescriptor
    if (resultingDescriptor.hasSynthesizedParameterNames() && resultingDescriptor !is FunctionInvokeDescriptor) {
        return emptyList()
    }

    if (resultingDescriptor.valueParameters.size == 1
        && resultingDescriptor.name == resultingDescriptor.valueParameters.single().name) {
        // method name equals to single parameter name
        return emptyList()
    }

    return resolvedCall.valueArguments.mapNotNull { (valueParam: ValueParameterDescriptor, resolvedArg) ->
        if (resultingDescriptor.isAnnotationConstructor() && valueParam.name.asString() == "value") {
            return@mapNotNull null
        }

        if (resultingDescriptor is FunctionInvokeDescriptor &&
            valueParam.type.extractParameterNameFromFunctionTypeArgument() == null
        ) {
            return@mapNotNull null
        }

        resolvedArg.arguments.firstOrNull()?.let { arg ->
            arg.getArgumentExpression()?.let { argExp ->
                if (!arg.isNamed() && !argExp.isAnnotatedWithComment(valueParam, resultingDescriptor) && !valueParam.name.isSpecial && argExp.isUnclearExpression()) {
                    val prefix = if (valueParam.varargElementType != null) "..." else ""
                    val offset = if (arg == valueArgumentList?.arguments?.firstOrNull() && valueParam.varargElementType != null)
                        valueArgumentList.leftParenthesis?.textRange?.endOffset ?: argExp.startOffset
                    else
                        arg.getSpreadElement()?.startOffset ?: argExp.startOffset
                    return@mapNotNull InlayInfo(prefix + valueParam.name.identifier + ":", offset)
                }
            }
        }
        null
    }
}

private fun KtExpression.isUnclearExpression() = when (this) {
    is KtConstantExpression, is KtThisExpression, is KtBinaryExpression, is KtStringTemplateExpression -> true
    is KtPrefixExpression -> baseExpression is KtConstantExpression && (operationToken == KtTokens.PLUS || operationToken == KtTokens.MINUS)
    else -> false
}

private fun KtExpression.isAnnotatedWithComment(valueParameter: ValueParameterDescriptor, descriptor: CallableDescriptor): Boolean =
    (descriptor is JavaMethodDescriptor || descriptor is JavaClassConstructorDescriptor) &&
            prevLeafs
                .takeWhile { it is PsiWhiteSpace || it is PsiComment }
                .any { it is PsiComment && it.text == valueParameter.toCommentedParameterName() }