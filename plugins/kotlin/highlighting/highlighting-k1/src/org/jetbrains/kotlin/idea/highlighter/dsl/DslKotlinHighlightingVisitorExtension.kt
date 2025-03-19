// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter.dsl

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.highlighting.dsl.DslStyleUtils
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingVisitorExtension
import org.jetbrains.kotlin.resolve.calls.DslMarkerUtils
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class DslKotlinHighlightingVisitorExtension : KotlinHighlightingVisitorExtension() {
    override fun highlightDeclaration(elementToHighlight: PsiElement, descriptor: DeclarationDescriptor): HighlightInfoType? {
        return null
    }

    override fun highlightCall(elementToHighlight: PsiElement, resolvedCall: ResolvedCall<*>): HighlightInfoType? {
        return dslCustomTextStyle(resolvedCall.resultingDescriptor)
    }

    @Suppress("UNUSED")
    companion object {
        fun externalKeyName(index: Int) = "KOTLIN_DSL_STYLE$index"

        // These methods were not moved to preserve compatibility
        fun styleOptionDisplayName(index: Int) = DslStyleUtils.styleOptionDisplayName(index)
        val descriptionsToStyles = DslStyleUtils.descriptionsToStyles
        fun styleById(styleId: Int): TextAttributesKey = DslStyleUtils.styleById(styleId)

        fun styleIdByMarkerAnnotation(markerAnnotation: ClassDescriptor): Int {
            return DslStyleUtils.styleIdByFQName(markerAnnotation.fqNameSafe)
        }

        fun dslCustomTextStyle(callableDescriptor: CallableDescriptor): HighlightInfoType? {
            val markerAnnotation = callableDescriptor.annotations.find { annotation ->
                annotation.annotationClass?.isDslHighlightingMarker() ?: false
            }?.annotationClass ?: return null

            val styleId = styleIdByMarkerAnnotation(markerAnnotation)
            return DslStyleUtils.typeById(styleId)
        }
    }
}

@ApiStatus.Internal
fun ClassDescriptor.isDslHighlightingMarker(): Boolean {
    return annotations.any {
        it.annotationClass?.fqNameSafe == DslMarkerUtils.DSL_MARKER_FQ_NAME
    }
}
