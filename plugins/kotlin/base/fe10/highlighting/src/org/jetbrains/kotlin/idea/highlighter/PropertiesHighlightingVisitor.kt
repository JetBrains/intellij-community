// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.extensions.Extensions
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.impl.SyntheticFieldDescriptor
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors.*
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.tasks.isDynamic
import org.jetbrains.kotlin.resolve.calls.tower.isSynthesized

internal class PropertiesHighlightingVisitor(holder: HighlightInfoHolder, bindingContext: BindingContext) :
    AfterAnalysisHighlightingVisitor(holder, bindingContext) {

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        if (expression.parent is KtThisExpression) {
            return
        }
        val target = bindingContext.get(BindingContext.REFERENCE_TARGET, expression)
        if (target is SyntheticFieldDescriptor) {
            highlightName(expression, BACKING_FIELD_VARIABLE)
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
        } ?: attributeKeyByPropertyType(target)

        if (attributesKey != null) {
            highlightName(expression, attributesKey)
        }
    }

    override fun visitProperty(property: KtProperty) {
        val nameIdentifier = property.nameIdentifier ?: return
        val propertyDescriptor = bindingContext.get(BindingContext.VARIABLE, property)
        if (propertyDescriptor is PropertyDescriptor) {
            highlightPropertyDeclaration(nameIdentifier, propertyDescriptor)
        }

        super.visitProperty(property)
    }

    override fun visitParameter(parameter: KtParameter) {
        val nameIdentifier = parameter.nameIdentifier ?: return
        val propertyDescriptor = bindingContext.get(BindingContext.PRIMARY_CONSTRUCTOR_PARAMETER, parameter)
        if (propertyDescriptor != null) {
            if (propertyDescriptor.isVar) {
                highlightName(nameIdentifier, MUTABLE_VARIABLE)
            }
            highlightPropertyDeclaration(nameIdentifier, propertyDescriptor)
        }

        super.visitParameter(parameter)
    }

    private fun highlightPropertyDeclaration(
        elementToHighlight: PsiElement,
        descriptor: PropertyDescriptor
    ) {
        val textAttributesKey =
            attributeKeyForDeclarationFromExtensions(elementToHighlight, descriptor) ?: attributeKeyByPropertyType(descriptor)

        if (textAttributesKey != null) {
            highlightName(
                elementToHighlight,
                textAttributesKey
            )
        }
    }

    private fun attributeKeyByPropertyType(descriptor: PropertyDescriptor): TextAttributesKey? = when {
        descriptor.isDynamic() ->
            // The property is set in VariablesHighlightingVisitor
            null

        hasExtensionReceiverParameter(descriptor) ->
            if (descriptor.isSynthesized) SYNTHETIC_EXTENSION_PROPERTY else EXTENSION_PROPERTY

        DescriptorUtils.isStaticDeclaration(descriptor) ->
            if (hasCustomPropertyDeclaration(descriptor))
                PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
            else
                PACKAGE_PROPERTY

        else ->
            if (hasCustomPropertyDeclaration(descriptor))
                INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
            else
                INSTANCE_PROPERTY
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