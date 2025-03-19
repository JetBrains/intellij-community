// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.extensions.Extensions
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.impl.SyntheticFieldDescriptor
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.tasks.isDynamic
import org.jetbrains.kotlin.resolve.calls.tower.isSynthesized
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

internal class PropertiesHighlightingVisitor(holder: HighlightInfoHolder, bindingContext: BindingContext) :
    AfterAnalysisHighlightingVisitor(holder, bindingContext) {

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        if (expression.parent is KtThisExpression) {
            return
        }
        val target = bindingContext.get(BindingContext.REFERENCE_TARGET, expression)
        if (target is SyntheticFieldDescriptor) {
            highlightName(expression, KotlinHighlightInfoTypeSemanticNames.BACKING_FIELD_VARIABLE)
            return
        }
        if (target !is PropertyDescriptor) {
            return
        }

        val resolvedCall = expression.getResolvedCall(bindingContext)

        val attributesKey = resolvedCall?.let { call ->
            @Suppress("DEPRECATION")
            Extensions.getExtensions(KotlinHighlightingVisitorExtension.EP_NAME).firstNotNullOfOrNull { extension ->
                extension.highlightCall(expression, call)
            }
        }

        val highlightInfoType = attributesKey ?: attributeKeyByPropertyType(target)
        if (highlightInfoType != null) {
            highlightName(expression, highlightInfoType)
        }
    }

    private fun attributeKeyByPropertyType(descriptor: PropertyDescriptor): HighlightInfoType? = when {
        descriptor.isDynamic() ->
            // The property is set in VariablesHighlightingVisitor
            null

        hasExtensionReceiverParameter(descriptor) ->
            if (descriptor.isSynthesized) KotlinHighlightInfoTypeSemanticNames.SYNTHETIC_EXTENSION_PROPERTY else KotlinHighlightInfoTypeSemanticNames.EXTENSION_PROPERTY

        DescriptorUtils.isStaticDeclaration(descriptor) ->
            if (hasCustomPropertyDeclaration(descriptor))
                KotlinHighlightInfoTypeSemanticNames.PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
            else
                KotlinHighlightInfoTypeSemanticNames.PACKAGE_PROPERTY

        else ->
            if (hasCustomPropertyDeclaration(descriptor))
                KotlinHighlightInfoTypeSemanticNames.INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
            else
                KotlinHighlightInfoTypeSemanticNames.INSTANCE_PROPERTY
    }
}

internal fun hasCustomPropertyDeclaration(descriptor: PropertyDescriptor): Boolean {
    var hasCustomPropertyDeclaration = false
    if (!hasExtensionReceiverParameter(descriptor)) {
        if (descriptor.getter?.isDefault == false || descriptor.setter?.isDefault == false)
            hasCustomPropertyDeclaration = true
    }
    return hasCustomPropertyDeclaration
}

internal fun hasExtensionReceiverParameter(descriptor: PropertyDescriptor): Boolean {
    return descriptor.extensionReceiverParameter != null
}