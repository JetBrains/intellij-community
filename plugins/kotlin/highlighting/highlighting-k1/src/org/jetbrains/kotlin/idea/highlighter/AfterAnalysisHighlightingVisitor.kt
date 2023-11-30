// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.extensions.Extensions
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.highlighting.visitor.AbstractHighlightingVisitor
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

abstract class AfterAnalysisHighlightingVisitor protected constructor(
    holder: HighlightInfoHolder,
    protected var bindingContext: BindingContext
) : AbstractHighlightingVisitor(holder) {

    protected fun attributeKeyForDeclarationFromExtensions(element: PsiElement, descriptor: DeclarationDescriptor): HighlightInfoType? {
        @Suppress("DEPRECATION")
        return Extensions.getExtensions(KotlinHighlightingVisitorExtension.EP_NAME).firstNotNullOfOrNull { extension ->
            extension.highlightDeclaration(element, descriptor)
        }
    }

    protected fun attributeKeyForCallFromExtensions(
        expression: KtSimpleNameExpression,
        resolvedCall: ResolvedCall<out CallableDescriptor>
    ): HighlightInfoType? {
        @Suppress("DEPRECATION")
        return Extensions.getExtensions(KotlinHighlightingVisitorExtension.EP_NAME).firstNotNullOfOrNull { extension ->
            extension.highlightCall(expression, resolvedCall)
        }
    }
}
