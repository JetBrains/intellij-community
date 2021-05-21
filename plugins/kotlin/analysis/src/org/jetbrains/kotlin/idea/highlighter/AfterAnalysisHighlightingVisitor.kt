// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.extensions.Extensions
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.util.firstNotNullResult

abstract class AfterAnalysisHighlightingVisitor protected constructor(
    holder: HighlightInfoHolder, protected var bindingContext: BindingContext
) : AbstractHighlightInfoHolderHighlightingVisitor(holder) {

    protected fun attributeKeyForDeclarationFromExtensions(element: PsiElement, descriptor: DeclarationDescriptor): TextAttributesKey? {
        @Suppress("DEPRECATION")
        return Extensions.getExtensions(HighlighterExtension.EP_NAME).firstNotNullResult { extension ->
            extension.highlightDeclaration(element, descriptor)
        }
    }

    protected fun attributeKeyForCallFromExtensions(
        expression: KtSimpleNameExpression,
        resolvedCall: ResolvedCall<out CallableDescriptor>
    ): TextAttributesKey? {
        @Suppress("DEPRECATION")
        return Extensions.getExtensions(HighlighterExtension.EP_NAME).firstNotNullResult { extension ->
            extension.highlightCall(expression, resolvedCall)
        }
    }
}
