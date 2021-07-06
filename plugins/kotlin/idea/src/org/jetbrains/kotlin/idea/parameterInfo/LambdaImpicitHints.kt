// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.TokenType
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeInsight.hints.HintType
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isUnit

fun provideLambdaImplicitHints(lambda: KtLambdaExpression): List<HintType.InlayInfoDetails> {
    val lbrace = lambda.leftCurlyBrace
    if (!lbrace.isFollowedByNewLine()) {
        return emptyList()
    }
    val bindingContext = lambda.analyze(BodyResolveMode.PARTIAL)
    val functionDescriptor = bindingContext[BindingContext.FUNCTION, lambda.functionLiteral] ?: return emptyList()

    functionDescriptor.extensionReceiverParameter?.let { implicitReceiver ->
        val type = implicitReceiver.type
        val renderType = getInlayHintsTypeRenderer(bindingContext, lambda).renderType(type)
        val text = buildString {
            append("this: ")
            append(renderType)
        }
        val inlayInfo = InlayInfo(text, lbrace.textRange.endOffset)
        return listOf(HintType.TypedInlayInfoDetails(inlayInfo, type))
    }

    val singleParameter = functionDescriptor.valueParameters.singleOrNull()
    if (singleParameter != null && bindingContext[BindingContext.AUTO_CREATED_IT, singleParameter] == true) {
        val type = singleParameter.type
        if (type.isUnit()) return emptyList()
        val renderType = getInlayHintsTypeRenderer(bindingContext, lambda).renderType(type)
        val text = buildString {
            append("it: ")
            append(renderType)
        }
        return listOf(HintType.TypedInlayInfoDetails(InlayInfo(text, lbrace.textRange.endOffset), type))
    }
    return emptyList()
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
