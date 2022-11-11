// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter.dsl

import com.intellij.ide.highlighter.custom.CustomHighlighterColors.*
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.fe10.highlighting.KotlinBaseFe10HighlightingBundle
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingVisitorExtension
import org.jetbrains.kotlin.resolve.calls.DslMarkerUtils
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import kotlin.math.absoluteValue

class DslKotlinHighlightingVisitorExtension : KotlinHighlightingVisitorExtension() {
    override fun highlightDeclaration(elementToHighlight: PsiElement, descriptor: DeclarationDescriptor): TextAttributesKey? {
        return null
    }

    override fun highlightCall(elementToHighlight: PsiElement, resolvedCall: ResolvedCall<*>): TextAttributesKey? {
        return dslCustomTextStyle(resolvedCall.resultingDescriptor)
    }

    companion object {
        private const val STYLE_COUNT = 4

        private val STYLE_KEYS = listOf(
            CUSTOM_KEYWORD1_ATTRIBUTES,
            CUSTOM_KEYWORD2_ATTRIBUTES,
            CUSTOM_KEYWORD3_ATTRIBUTES,
            CUSTOM_KEYWORD4_ATTRIBUTES
        )

        private val styles = (1..STYLE_COUNT).map { index ->
            TextAttributesKey.createTextAttributesKey(externalKeyName(index), STYLE_KEYS[index - 1])
        }

        val descriptionsToStyles = (1..STYLE_COUNT).associate { index ->
            KotlinBaseFe10HighlightingBundle.message("highlighter.name.dsl") + styleOptionDisplayName(index) to styleById(index)
        }

        fun externalKeyName(index: Int) = "KOTLIN_DSL_STYLE$index"

        fun styleOptionDisplayName(index: Int) = KotlinBaseFe10HighlightingBundle.message("highlighter.name.style") + index

        fun styleIdByMarkerAnnotation(markerAnnotation: ClassDescriptor): Int {
            val markerAnnotationFqName = markerAnnotation.fqNameSafe
            return (markerAnnotationFqName.asString().hashCode() % STYLE_COUNT).absoluteValue + 1
        }

        fun dslCustomTextStyle(callableDescriptor: CallableDescriptor): TextAttributesKey? {
            val markerAnnotation = callableDescriptor.annotations.find { annotation ->
                annotation.annotationClass?.isDslHighlightingMarker() ?: false
            }?.annotationClass ?: return null

            val styleId = styleIdByMarkerAnnotation(markerAnnotation)
            return styleById(styleId)
        }

        fun styleById(styleId: Int): TextAttributesKey = styles[styleId - 1]
    }
}

@ApiStatus.Internal
fun ClassDescriptor.isDslHighlightingMarker(): Boolean {
    return annotations.any {
        it.annotationClass?.fqNameSafe == DslMarkerUtils.DSL_MARKER_FQ_NAME
    }
}
