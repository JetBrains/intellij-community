// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

abstract class KotlinHighlightingVisitorExtension {
    abstract fun highlightDeclaration(elementToHighlight: PsiElement, descriptor: DeclarationDescriptor): HighlightInfoType?

    open fun highlightCall(elementToHighlight: PsiElement, resolvedCall: ResolvedCall<*>): HighlightInfoType? {
        return highlightDeclaration(elementToHighlight, resolvedCall.resultingDescriptor)
    }

    companion object {
        val EP_NAME = ExtensionPointName.create<KotlinHighlightingVisitorExtension>("org.jetbrains.kotlin.highlighterExtension")
    }
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Extend 'KotlinHighlightingVisitorExtension' instead", level = DeprecationLevel.ERROR)
abstract class HighlighterExtension : KotlinHighlightingVisitorExtension()