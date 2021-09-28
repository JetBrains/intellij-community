// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.AnonymousFunctionDescriptor
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.core.KotlinNameSuggester
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.sam.JavaSingleAbstractMethodUtils
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.sam.getSingleAbstractMethodOrNull
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.isFlexible
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class SamConversionToAnonymousObjectIntention : SelfTargetingRangeIntention<KtCallExpression>(
    KtCallExpression::class.java, KotlinBundle.lazyMessage("convert.to.anonymous.object")
), LowPriorityAction {
    override fun applicabilityRange(element: KtCallExpression): TextRange? {
        val callee = element.calleeExpression ?: return null
        val lambda = getLambdaExpression(element) ?: return null
        val functionLiteral = lambda.functionLiteral
        val bindingContext = functionLiteral.analyze()
        val sam = element.getSingleAbstractMethod(bindingContext) ?: return null

        val samValueParameters = sam.valueParameters
        val samValueParameterSize = samValueParameters.size
        if (samValueParameterSize != functionLiteral.functionDescriptor(bindingContext)?.valueParameters?.size) return null

        val samName = sam.name.asString()
        if (functionLiteral.anyDescendantOfType<KtCallExpression> { call ->
                if (call.calleeExpression?.text != samName) return@anyDescendantOfType false
                val valueArguments = call.valueArguments
                if (valueArguments.size != samValueParameterSize) return@anyDescendantOfType false
                val context = call.analyze(BodyResolveMode.PARTIAL)
                valueArguments.zip(samValueParameters).all { (arg, param) ->
                    arg.getArgumentExpression()?.getType(context)?.isSubtypeOf(param.type) == true
                }
            }) return null

        if (bindingContext.diagnostics.forElement(functionLiteral).any { it.severity == Severity.ERROR }) return null

        if (callee.mainReference.isAliasedWithVariance()) return null

        return callee.textRange
    }

    private fun KtReference?.isAliasedWithVariance(): Boolean {
        val typeAlias = this?.resolve()
            ?.safeAs<KtTypeAlias>()
            ?.resolveToDescriptorIfAny()
            ?.safeAs<TypeAliasDescriptor>()
            ?: return false
         return typeAlias.expandedType.hasVariance()
    }

    override fun applyTo(element: KtCallExpression, editor: Editor?) {
        val lambda = getLambdaExpression(element) ?: return
        val context = element.analyze(BodyResolveMode.PARTIAL)
        val lambdaFunctionDescriptor = lambda.functionLiteral.functionDescriptor(context) ?: return
        val samDescriptor = element.getSingleAbstractMethod(context) ?: return
        val classDescriptor = samDescriptor.containingDeclaration as? ClassDescriptor
        val typeParameters = typeParameters(element, context, classDescriptor, samDescriptor, lambdaFunctionDescriptor)
        convertToAnonymousObject(element, typeParameters, samDescriptor, lambda, lambdaFunctionDescriptor)
    }

    private fun KtCallExpression.getSingleAbstractMethod(context: BindingContext): FunctionDescriptor? {
        val type = getType(context) ?: return null
        if (!JavaSingleAbstractMethodUtils.isSamType(type)) return null
        val classDescriptor = type.constructor.declarationDescriptor as? ClassDescriptor ?: return null
        return getSingleAbstractMethodOrNull(classDescriptor)
    }

    private fun KtFunctionLiteral.functionDescriptor(context: BindingContext): AnonymousFunctionDescriptor? =
        context[BindingContext.FUNCTION, this] as? AnonymousFunctionDescriptor

    private fun KotlinType.hasVariance(): Boolean {
        return arguments.any {
            val projectKind = it.projectionKind
            projectKind == Variance.IN_VARIANCE || projectKind == Variance.OUT_VARIANCE || it.type.hasVariance()
        }
    }

    companion object {
        fun convertToAnonymousObject(
            call: KtCallExpression,
            typeParameters: Map<TypeConstructor, KotlinType>,
            samDescriptor: FunctionDescriptor,
            lambda: KtLambdaExpression,
            lambdaFunctionDescriptor: AnonymousFunctionDescriptor? = null
        ) {
            val parentOfCall = call.getQualifiedExpressionForSelector()
            val interfaceName = if (parentOfCall != null) {
                parentOfCall.resolveToCall()?.resultingDescriptor?.fqNameSafe?.asString()
            } else {
                call.calleeExpression?.text
            } ?: return

            val typeSourceCode = IdeDescriptorRenderers.SOURCE_CODE_TYPES
            val typeArguments = typeParameters.values.map { typeSourceCode.renderType(it) }
            val typeArgumentsText = if (typeArguments.isEmpty()) {
                ""
            } else {
                typeArguments.joinToString(prefix = "<", postfix = ">", separator = ", ")
            }

            val functionDescriptor = lambdaFunctionDescriptor ?: samDescriptor
            val functionName = samDescriptor.name.asString()
            val samParameters = samDescriptor.valueParameters
            val nameValidator = CollectingNameValidator(lambdaFunctionDescriptor?.valueParameters?.map { it.name.asString() }.orEmpty())
            val functionParameterName: (ValueParameterDescriptor, Int) -> String = { parameter, index ->
                val name = parameter.name
                if (name.isSpecial) {
                    KotlinNameSuggester.suggestNameByName((samParameters.getOrNull(index)?.name ?: name).asString(), nameValidator)
                } else {
                    name.asString()
                }
            }

            LambdaToAnonymousFunctionIntention.convertLambdaToFunction(
                lambda,
                functionDescriptor,
                functionName,
                functionParameterName,
                typeParameters
            ) {
                it.addModifier(KtTokens.OVERRIDE_KEYWORD)
                (parentOfCall ?: call).replaced(
                    KtPsiFactory(it).createExpression("object : $interfaceName$typeArgumentsText { ${it.text} }")
                )
            }
        }

        fun getLambdaExpression(element: KtCallExpression): KtLambdaExpression? =
            element.lambdaArguments.firstOrNull()?.getLambdaExpression()
                ?: element.valueArguments.firstOrNull()?.getArgumentExpression() as? KtLambdaExpression

        fun typeParameters(
            call: KtCallExpression,
            context: BindingContext,
            classDescriptor: ClassDescriptor?,
            functionDescriptor: FunctionDescriptor,
            lambdaDescriptor: FunctionDescriptor?
        ): Map<TypeConstructor, KotlinType> {
            val declaredTypeParameters = classDescriptor?.declaredTypeParameters.orEmpty()
            if (declaredTypeParameters.isEmpty()) return emptyMap()
            val typeArguments = call.typeArguments
            return if (typeArguments.isNotEmpty()) {
                declaredTypeParameters
                    .map { it.typeConstructor }
                    .zip(typeArguments.mapNotNull { context[BindingContext.TYPE, it.typeReference] })
                    .toMap()
            } else {
                computeTypeParameters(declaredTypeParameters, functionDescriptor, call, context, lambdaDescriptor)
            }
        }

        private fun computeTypeParameters(
            declaredTypeParameters: List<TypeParameterDescriptor>,
            functionDescriptor: FunctionDescriptor,
            call: KtCallExpression,
            context: BindingContext,
            lambdaDescriptor: FunctionDescriptor?
        ): Map<TypeConstructor, KotlinType> {
            if (lambdaDescriptor == null) return emptyMap()
            val functionParameterTypes = functionDescriptor.valueParameters.map { it.type }
            val functionReturnType = functionDescriptor.returnType
            val lambdaParameterTypes = lambdaDescriptor.valueParameters.map { it.type }
            val lambdaReturnType = lambdaDescriptor.returnType
            val resolvedCall = call.getResolvedCall(context)
            val types = resolvedCall?.typeArguments.orEmpty()
            val typeParameters = resolvedCall?.candidateDescriptor?.typeParameters ?: declaredTypeParameters

            return typeParameters.mapNotNull { typeParameter ->
                val typeConstructor = typeParameter.typeConstructor
                val type = types[typeParameter].let {
                    if (it != null && !it.isFlexible()) {
                        it
                    } else {
                        val typeConstructorName = typeConstructor.declarationDescriptor?.name
                        typeConstructorName?.actualType(functionParameterTypes, lambdaParameterTypes)
                            ?: typeConstructorName?.actualType(functionReturnType, lambdaReturnType)
                            ?: return@mapNotNull null
                    }
                }
                typeConstructor to type
            }.toMap()
        }

        private fun Name.actualType(functionTypes: List<KotlinType>, lambdaTypes: List<KotlinType>): KotlinType? {
            return functionTypes.zip(lambdaTypes).firstNotNullOfOrNull { actualType(it.first, it.second) }
        }

        private fun Name.actualType(functionType: KotlinType?, lambdaType: KotlinType?): KotlinType? {
            if (functionType == null || lambdaType == null) return null
            return if (this == functionType.constructor.declarationDescriptor?.name) {
                lambdaType
            } else {
                actualType(functionType.arguments.map { it.type }, lambdaType.arguments.map { it.type })
            }
        }
    }
}