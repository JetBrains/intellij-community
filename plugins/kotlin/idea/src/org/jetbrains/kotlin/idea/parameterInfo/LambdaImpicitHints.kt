// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.TokenType
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeInsight.hints.InlayInfoDetails
import org.jetbrains.kotlin.idea.codeInsight.hints.TextInlayInfoDetail
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isUnit

fun provideLambdaImplicitHints(lambda: KtLambdaExpression): InlayInfoDetails? {
    val lbrace = lambda.leftCurlyBrace
    if (!lbrace.isFollowedByNewLine()) {
        return null
    }
    val bindingContext = lambda.analyze(BodyResolveMode.PARTIAL)
    val functionDescriptor = bindingContext[BindingContext.FUNCTION, lambda.functionLiteral] ?: return null

    functionDescriptor.extensionReceiverParameter?.let { implicitReceiver ->
        val type = implicitReceiver.type
        val renderedType = HintsTypeRenderer.getInlayHintsTypeRenderer(bindingContext, lambda).renderTypeIntoInlayInfo(type)
        return InlayInfoDetails(InlayInfo("", lbrace.textRange.endOffset), listOf(TextInlayInfoDetail("this: ")) + renderedType)
    }

    val singleParameter = functionDescriptor.valueParameters.singleOrNull()
    if (singleParameter != null && bindingContext[BindingContext.AUTO_CREATED_IT, singleParameter] == true) {
        val type = singleParameter.type
        if (type.isUnit()) return null
        val renderedType = HintsTypeRenderer.getInlayHintsTypeRenderer(bindingContext, lambda).renderTypeIntoInlayInfo(type)
        return InlayInfoDetails(InlayInfo("", lbrace.textRange.endOffset), listOf(TextInlayInfoDetail("it: ")) + renderedType)
    }
    return null
}

private fun ASTNode.isFollowedByNewLine(): Boolean {
    for (sibling in siblings()) {
        if (sibling.elementType != TokenType.WHITE_SPACE && sibling.psi !is PsiComment) {
            return false
        }
        if (sibling.elementType == TokenType.WHITE_SPACE && sibling.textContains('\n')) {
            return true
        }
    }
    return false
}
