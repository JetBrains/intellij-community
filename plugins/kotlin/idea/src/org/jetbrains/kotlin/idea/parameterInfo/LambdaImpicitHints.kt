// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.TokenType
import org.jetbrains.kotlin.idea.codeInsight.hints.InlayInfoDetails
import org.jetbrains.kotlin.idea.codeInsight.hints.TextInlayInfoDetail
import org.jetbrains.kotlin.idea.util.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isUnit

fun provideLambdaImplicitHints(lambda: KtLambdaExpression): List<InlayInfoDetails>? {
    val lbrace = lambda.leftCurlyBrace
    if (!lbrace.isFollowedByNewLine()) {
        return null
    }
    val bindingContext = lambda.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)
    val functionDescriptor = bindingContext[BindingContext.FUNCTION, lambda.functionLiteral] ?: return null

    val implicitReceiverHint = functionDescriptor.extensionReceiverParameter?.let { implicitReceiver ->
        val type = implicitReceiver.type
        val renderedType = HintsTypeRenderer.getInlayHintsTypeRenderer(bindingContext, lambda).renderTypeIntoInlayInfo(type)
        InlayInfoDetails(InlayInfo("", lbrace.psi.textRange.endOffset), listOf(TextInlayInfoDetail("this: ")) + renderedType)
    }

    val singleParameter = functionDescriptor.valueParameters.singleOrNull()
    val singleParameterHint = if (singleParameter != null && bindingContext[BindingContext.AUTO_CREATED_IT, singleParameter] == true) {
        val type = singleParameter.type
        if (type.isUnit()) null else {
            val renderedType = HintsTypeRenderer.getInlayHintsTypeRenderer(bindingContext, lambda).renderTypeIntoInlayInfo(type)
            InlayInfoDetails(InlayInfo("", lbrace.textRange.endOffset), listOf(TextInlayInfoDetail("it: ")) + renderedType)
        }
    } else null

    return listOfNotNull(implicitReceiverHint, singleParameterHint)
}

private fun ASTNode.isFollowedByNewLine(): Boolean {
    for (sibling in siblings()) {
        if (sibling.elementType != TokenType.WHITE_SPACE && sibling.psi !is PsiComment) {
            continue
        }
        if (sibling.elementType == TokenType.WHITE_SPACE && sibling.textContains('\n')) {
            return true
        }
    }
    return false
}
