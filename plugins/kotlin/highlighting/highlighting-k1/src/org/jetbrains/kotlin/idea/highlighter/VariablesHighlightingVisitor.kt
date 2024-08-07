// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.SyntheticFieldDescriptor
import org.jetbrains.kotlin.idea.base.fe10.highlighting.KotlinBaseFe10HighlightingBundle
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.base.highlighting.textAttributesForKtPropertyDeclaration
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingContext.*
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.smartcasts.MultipleSmartCasts
import org.jetbrains.kotlin.resolve.calls.tasks.isDynamic
import org.jetbrains.kotlin.resolve.scopes.receivers.ExtensionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitClassReceiver
import org.jetbrains.kotlin.types.expressions.CaptureKind
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class VariablesHighlightingVisitor(holder: HighlightInfoHolder, bindingContext: BindingContext) :
    AfterAnalysisHighlightingVisitor(holder, bindingContext) {

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        val target = bindingContext.get(REFERENCE_TARGET, expression) ?: return
        if (target is ValueParameterDescriptor && bindingContext.get(AUTO_CREATED_IT, target) == true) {
            highlightName(
              expression,
              KotlinHighlightInfoTypeSemanticNames.FUNCTION_LITERAL_DEFAULT_PARAMETER,
              KotlinBaseHighlightingBundle.message("automatically.declared.based.on.the.expected.type")
            )
        } else if (expression.parent !is KtValueArgumentName) { // highlighted separately
            highlightVariable(expression, target)
        }

        super.visitSimpleNameExpression(expression)
    }

    override fun visitProperty(property: KtProperty) {
        visitVariableDeclaration(property)
        super.visitProperty(property)
    }

    override fun visitParameter(parameter: KtParameter) {
        val propertyDescriptor = bindingContext.get(PRIMARY_CONSTRUCTOR_PARAMETER, parameter)
        if (propertyDescriptor == null) {
            visitVariableDeclaration(parameter)
        }
        super.visitParameter(parameter)
    }

    override fun visitDestructuringDeclarationEntry(multiDeclarationEntry: KtDestructuringDeclarationEntry) {
        visitVariableDeclaration(multiDeclarationEntry)
        super.visitDestructuringDeclarationEntry(multiDeclarationEntry)
    }

    private fun getSmartCastTarget(expression: KtExpression): PsiElement {
        var target: PsiElement = expression
        if (target is KtParenthesizedExpression) {
            target = KtPsiUtil.deparenthesize(target) ?: expression
        }
        return when (target) {
            is KtIfExpression -> target.ifKeyword
            is KtWhenExpression -> target.whenKeyword
            is KtBinaryExpression -> target.operationReference
            else -> target
        }
    }

    override fun visitExpression(expression: KtExpression) {
        val implicitSmartCast = bindingContext.get(IMPLICIT_RECEIVER_SMARTCAST, expression)
        if (implicitSmartCast != null) {
            for ((receiver, type) in implicitSmartCast.receiverTypes) {
                val receiverName = when (receiver) {
                    is ExtensionReceiver -> KotlinBaseHighlightingBundle.message("extension.implicit.receiver")
                    is ImplicitClassReceiver -> KotlinBaseHighlightingBundle.message("implicit.receiver")
                    else -> KotlinBaseFe10HighlightingBundle.message("unknown.receiver")
                }
                highlightName(
                  expression,
                  KotlinHighlightInfoTypeSemanticNames.SMART_CAST_RECEIVER,
                  KotlinBaseHighlightingBundle.message(
                      "0.smart.cast.to.1",
                      receiverName,
                      DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(type)
                  )
                )
            }
        }

        val nullSmartCast = bindingContext.get(SMARTCAST_NULL, expression) == true
        if (nullSmartCast) {
            highlightName(expression, KotlinHighlightInfoTypeSemanticNames.SMART_CONSTANT, KotlinBaseFe10HighlightingBundle.message("always.null"))
        }

        val smartCast = bindingContext.get(SMARTCAST, expression)
        if (smartCast != null) {
            val defaultType = smartCast.defaultType
            if (defaultType != null) {
                highlightName(
                  getSmartCastTarget(expression),
                  KotlinHighlightInfoTypeSemanticNames.SMART_CAST_VALUE,
                  KotlinBaseHighlightingBundle.message("smart.cast.to.0", DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(defaultType))
                )
            } else if (smartCast is MultipleSmartCasts) {
                for ((call, type) in smartCast.map) {
                    highlightName(
                      getSmartCastTarget(expression),
                      KotlinHighlightInfoTypeSemanticNames.SMART_CAST_VALUE,
                      KotlinBaseFe10HighlightingBundle.message(
                        "smart.cast.to.0.for.1.call",
                        DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(type),
                        (call?.callElement as? PsiNamedElement)?.name?:"null"
                      )
                    )
                }
            }
        }

        super.visitExpression(expression)
    }

    private fun visitVariableDeclaration(declaration: KtNamedDeclaration) {
        val declarationDescriptor = bindingContext.get(DECLARATION_TO_DESCRIPTOR, declaration)
        val nameIdentifier = declaration.nameIdentifier
        if (nameIdentifier != null && declarationDescriptor != null) {
            highlightVariable(nameIdentifier, declarationDescriptor)
        }
    }

    private fun highlightVariable(elementToHighlight: PsiElement, descriptor: DeclarationDescriptor) {
        if (descriptor is VariableDescriptor) {

            if (descriptor.isDynamic()) {
                highlightName(elementToHighlight, KotlinHighlightInfoTypeSemanticNames.DYNAMIC_PROPERTY_CALL)
                return
            }

            if (descriptor.isVar) {
                val shouldHighlight = if (elementToHighlight is KtNameReferenceExpression) {
                    val setter = descriptor.safeAs<PropertyDescriptor>()?.setter
                    setter == null || setter.isVisible(
                        elementToHighlight, elementToHighlight.getReceiverExpression(), bindingContext,
                        elementToHighlight.getResolutionFacade()
                    )
                } else true
                if (shouldHighlight
                    && (elementToHighlight.parent as? KtProperty)?.nameIdentifier != elementToHighlight
                    && (elementToHighlight.parent as? KtParameter)?.nameIdentifier != elementToHighlight
                    && (elementToHighlight.parent as? KtDestructuringDeclarationEntry)?.nameIdentifier != elementToHighlight
                    ) { // was highlighted in DeclarationHighlightingVisitor
                    highlightName(elementToHighlight, KotlinHighlightInfoTypeSemanticNames.MUTABLE_VARIABLE)
                }
            }

            if (bindingContext.get(CAPTURED_IN_CLOSURE, descriptor) == CaptureKind.NOT_INLINE) {
                val msg = if (descriptor.isVar)
                    KotlinBaseFe10HighlightingBundle.message("wrapped.into.a.reference.object.to.be.modified.when.captured.in.a.closure")
                else
                    KotlinBaseFe10HighlightingBundle.message("value.captured.in.a.closure")

                val parent = elementToHighlight.parent
                if (!(parent is PsiNameIdentifierOwner && parent.nameIdentifier == elementToHighlight)) {
                    highlightName(elementToHighlight, KotlinHighlightInfoTypeSemanticNames.WRAPPED_INTO_REF, msg)
                    return
                }
            }

            val declaration = elementToHighlight.parent
            if (descriptor is LocalVariableDescriptor && descriptor !is SyntheticFieldDescriptor && !(
                                declaration is KtProperty && declaration.nameIdentifier == elementToHighlight && textAttributesForKtPropertyDeclaration(declaration) == KotlinHighlightInfoTypeSemanticNames.LOCAL_VARIABLE
                                || declaration is KtDestructuringDeclarationEntry && declaration.nameIdentifier == elementToHighlight)) {
                // local was highlighted in DeclarationHighlightingVisitor
                highlightName(elementToHighlight, KotlinHighlightInfoTypeSemanticNames.LOCAL_VARIABLE)
            }

            if (descriptor is ValueParameterDescriptor && elementToHighlight.parent !is KtParameter) { // KtParameter was highlighted in DeclarationHighlightingVisitor
                highlightName(elementToHighlight, KotlinHighlightInfoTypeSemanticNames.PARAMETER)
            }

            if (descriptor is PropertyDescriptor && hasCustomPropertyDeclaration(descriptor) && (declaration as? KtProperty)?.nameIdentifier != elementToHighlight && elementToHighlight !is KtSimpleNameExpression) { // KtProperty was highlighted in DeclarationHighlightingVisitor
                val isStaticDeclaration = DescriptorUtils.isStaticDeclaration(descriptor)
                highlightName(
                    elementToHighlight,
                    if (isStaticDeclaration)
                        KotlinHighlightInfoTypeSemanticNames.PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
                    else
                        KotlinHighlightInfoTypeSemanticNames.INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
                )
            }
        }
    }
}
