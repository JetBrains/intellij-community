// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.parameterInfo.KotlinIdeDescriptorRenderer
import org.jetbrains.kotlin.idea.resolve.dataFlowValueFactory
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.renderer.RenderingFormat
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.util.getParameterForArgument
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.expressions.typeInfoFactory.noTypeInfo


class KotlinExpressionTypeProviderDescriptorsImpl : KotlinExpressionTypeProvider() {

    private val typeRenderer = KotlinIdeDescriptorRenderer.withOptions {
        textFormat = RenderingFormat.HTML
        modifiers = emptySet()
        parameterNameRenderingPolicy = ParameterNameRenderingPolicy.ONLY_NON_SYNTHESIZED
        classifierNamePolicy = object : ClassifierNamePolicy {
            override fun renderClassifier(classifier: ClassifierDescriptor, renderer: DescriptorRenderer): String {
                if (DescriptorUtils.isAnonymousObject(classifier)) {
                    return "&lt;" + KotlinBundle.message("type.provider.anonymous.object") + "&gt;"
                }
                return ClassifierNamePolicy.SHORT.renderClassifier(classifier, renderer)
            }
        }
    }

    override fun KtExpression.shouldShowStatementType(): Boolean {
        if (parent !is KtBlockExpression) return true
        if (parent.children.lastOrNull() == this) {
            return isUsedAsExpression(analyze(BodyResolveMode.PARTIAL_WITH_CFA))
        }
        return false
    }


    override fun getInformationHint(element: KtExpression): String {
        val bindingContext = element.analyze(BodyResolveMode.PARTIAL)

        return "<html>${renderExpressionType(element, bindingContext)}</html>"
    }

    @NlsSafe
    private fun renderExpressionType(element: KtExpression, bindingContext: BindingContext): String {
        if (element is KtCallableDeclaration) {
            val descriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, element] as? CallableDescriptor
            if (descriptor != null) {
                return descriptor.returnType?.let { typeRenderer.renderType(it) } ?: KotlinBundle.message("type.provider.unknown.type")
            }
        }

        val expressionTypeInfo = bindingContext[BindingContext.EXPRESSION_TYPE_INFO, element] ?: noTypeInfo(DataFlowInfo.EMPTY)
        val expressionType = element.getType(bindingContext) ?: getTypeForArgumentName(element, bindingContext)

        val result = expressionType?.let { typeRenderer.renderType(it) } ?: return KotlinBundle.message("type.provider.unknown.type")

        val dataFlowValueFactory = element.getResolutionFacade().dataFlowValueFactory
        val dataFlowValue =
            dataFlowValueFactory.createDataFlowValue(element, expressionType, bindingContext, element.findModuleDescriptor())
        val types = expressionTypeInfo.dataFlowInfo.getStableTypes(dataFlowValue, element.languageVersionSettings)
        if (types.isNotEmpty()) {
            return types.joinToString(separator = " & ") { typeRenderer.renderType(it) } +
                    " " + KotlinBundle.message("type.provider.smart.cast.from", result)
        }

        val smartCast = bindingContext[BindingContext.SMARTCAST, element]
        if (smartCast != null && element is KtReferenceExpression) {
            val declaredType = (bindingContext[BindingContext.REFERENCE_TARGET, element] as? CallableDescriptor)?.returnType
            if (declaredType != null) {
                return result + " " + KotlinBundle.message("type.provider.smart.cast.from", typeRenderer.renderType(declaredType))
            }
        }
        return result
    }

    private fun getTypeForArgumentName(element: KtExpression, bindingContext: BindingContext): KotlinType? {
        val valueArgumentName = (element.parent as? KtValueArgumentName) ?: return null
        val argument = valueArgumentName.parent as? KtValueArgument ?: return null
        val ktCallExpression = argument.parents.filterIsInstance<KtCallExpression>().firstOrNull() ?: return null
        val resolvedCall = ktCallExpression.getResolvedCall(bindingContext) ?: return null
        val parameter = resolvedCall.getParameterForArgument(argument) ?: return null
        return parameter.type
    }

    override fun getErrorHint(): String = KotlinBundle.message("type.provider.no.expression.found")
}
