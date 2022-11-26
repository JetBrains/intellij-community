// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.builtins.*
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.setType
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveToDescriptors
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.approximateFlexibleTypes
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi2ir.deparenthesize
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingContext.FUNCTION
import org.jetbrains.kotlin.resolve.BindingContext.REFERENCE_TARGET
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.components.hasDefaultValue
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.calls.model.DefaultValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VarargValueArgument
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getParameterForArgument
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.isCompanionObject
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyPackageDescriptor
import org.jetbrains.kotlin.resolve.scopes.receivers.ExtensionReceiver
import org.jetbrains.kotlin.resolve.scopes.utils.getImplicitReceiversHierarchy
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
import org.jetbrains.kotlin.types.AbbreviatedType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isDynamic
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

@Suppress("DEPRECATION")
class ConvertLambdaToReferenceInspection : IntentionBasedInspection<KtLambdaExpression>(
    ConvertLambdaToReferenceIntention::class,
    problemText = KotlinBundle.message("convert.lambda.to.reference.before.text")
)

open class ConvertLambdaToReferenceIntention(textGetter: () -> String) : SelfTargetingOffsetIndependentIntention<KtLambdaExpression>(
    KtLambdaExpression::class.java,
    textGetter
) {
    @Suppress("unused")
    constructor() : this(KotlinBundle.lazyMessage("convert.lambda.to.reference"))

    private fun isConvertibleCallInLambda(
        callableExpression: KtExpression,
        explicitReceiver: KtExpression? = null,
        lambdaExpression: KtLambdaExpression
    ): Boolean {
        val languageVersionSettings = callableExpression.languageVersionSettings
        val calleeReferenceExpression = when (callableExpression) {
            is KtCallExpression -> callableExpression.calleeExpression as? KtNameReferenceExpression ?: return false
            is KtNameReferenceExpression -> callableExpression
            else -> return false
        }
        val context = callableExpression.safeAnalyzeNonSourceRootCode()

        if (explicitReceiver is KtSuperExpression || explicitReceiver?.isReferenceToPackage(context) == true) return false

        val calleeDescriptor =
            calleeReferenceExpression.getResolvedCall(context)?.resultingDescriptor as? CallableMemberDescriptor ?: return false

        val lambdaParameterType = lambdaExpression.lambdaParameterType(context)
        if (lambdaParameterType?.isExtensionFunctionType == true) {
            if (explicitReceiver != null && explicitReceiver !is KtThisExpression) return false
            if (lambdaParameterType.getReceiverTypeFromFunctionType() != calleeDescriptor.receiverType()) return false
        }

        val lambdaParameterIsSuspend = lambdaParameterType?.isSuspendFunctionType == true
        val calleeFunctionIsSuspend = (calleeDescriptor as? FunctionDescriptor)?.isSuspend == true
        if (!lambdaParameterIsSuspend && calleeFunctionIsSuspend) return false
        if (lambdaParameterIsSuspend && !calleeFunctionIsSuspend &&
            !languageVersionSettings.supportsFeature(LanguageFeature.SuspendConversion)
        ) return false

        // No references with type parameters
        if (calleeDescriptor.typeParameters.isNotEmpty() && lambdaExpression.parentValueArgument() == null) return false
        // No references to Java synthetic properties
        if (!languageVersionSettings.supportsFeature(LanguageFeature.ReferencesToSyntheticJavaProperties) &&
            calleeDescriptor is SyntheticJavaPropertyDescriptor) return false

        val descriptorHasReceiver = with(calleeDescriptor) {
            // No references to both member / extension
            if (dispatchReceiverParameter != null && extensionReceiverParameter != null) return false
            dispatchReceiverParameter != null || extensionReceiverParameter != null
        }
        val noBoundReferences = !languageVersionSettings.supportsFeature(LanguageFeature.BoundCallableReferences)
        if (noBoundReferences && descriptorHasReceiver && explicitReceiver == null) return false

        val callableArgumentsCount = (callableExpression as? KtCallExpression)?.valueArguments?.size ?: 0
        if (calleeDescriptor.valueParameters.size != callableArgumentsCount &&
            (lambdaExpression.parentValueArgument() == null || calleeDescriptor.valueParameters.none { it.declaresDefaultValue() })
        ) return false

        if (!lambdaExpression.isArgument() && calleeDescriptor is FunctionDescriptor && calleeDescriptor.overloadedFunctions().size > 1) {
            val property = lambdaExpression.getStrictParentOfType<KtProperty>()
            if (property != null && property.initializer?.deparenthesize() != lambdaExpression) return false
            val lambdaReturnType = context[BindingContext.EXPRESSION_TYPE_INFO, lambdaExpression]?.type?.arguments?.lastOrNull()?.type
            if (lambdaReturnType != calleeDescriptor.returnType) return false
        }

        val lambdaValueParameterDescriptors = context[FUNCTION, lambdaExpression.functionLiteral]?.valueParameters ?: return false
        if (explicitReceiver != null && explicitReceiver !is KtSimpleNameExpression &&
            explicitReceiver.anyDescendantOfType<KtSimpleNameExpression> {
                it.getResolvedCall(context)?.resultingDescriptor in lambdaValueParameterDescriptors
            }
        ) return false

        val explicitReceiverDescriptor = (explicitReceiver as? KtNameReferenceExpression)?.let { context[REFERENCE_TARGET, it] }
        val lambdaParameterAsExplicitReceiver = when (noBoundReferences) {
            true -> explicitReceiver != null
            false -> explicitReceiverDescriptor != null && explicitReceiverDescriptor == lambdaValueParameterDescriptors.firstOrNull()
        }
        val explicitReceiverShift = if (lambdaParameterAsExplicitReceiver) 1 else 0

        val lambdaParametersCount = lambdaValueParameterDescriptors.size
        if (lambdaParametersCount != callableArgumentsCount + explicitReceiverShift) return false

        if (explicitReceiver != null && explicitReceiverDescriptor is ValueDescriptor && lambdaParameterAsExplicitReceiver) {
            val receiverType = explicitReceiverDescriptor.type
            // No exotic receiver types
            if (receiverType.isTypeParameter() || receiverType.isError || receiverType.isDynamic() ||
                !receiverType.constructor.isDenotable || receiverType.isFunctionType
            ) return false
        }

        // Same lambda / references function parameter order
        if (callableExpression is KtCallExpression) {
            if (lambdaValueParameterDescriptors.size < explicitReceiverShift + callableExpression.valueArguments.size) return false
            val resolvedCall = callableExpression.getResolvedCall(context) ?: return false
            resolvedCall.valueArguments.entries.forEach { (valueParameter, resolvedArgument) ->
                if (resolvedArgument is DefaultValueArgument) return@forEach
                val argument = resolvedArgument.arguments.singleOrNull() ?: return false
                if (resolvedArgument is VarargValueArgument && argument.getSpreadElement() == null) return false
                val argumentExpression = argument.getArgumentExpression() as? KtNameReferenceExpression ?: return false
                val argumentTarget = context[REFERENCE_TARGET, argumentExpression] as? ValueParameterDescriptor ?: return false
                if (argumentTarget != lambdaValueParameterDescriptors[valueParameter.index + explicitReceiverShift]) return false
            }
        }
        return true
    }

    private fun KtExpression.isReferenceToPackage(context: BindingContext): Boolean {
        val selectorOrThis = (this as? KtQualifiedExpression)?.selectorExpression ?: this
        val descriptors = selectorOrThis.mainReference?.resolveToDescriptors(context) ?: return false
        return descriptors.any { it is PackageViewDescriptor }
    }

    override fun isApplicableTo(element: KtLambdaExpression): Boolean {
        val singleStatement = element.singleStatementOrNull() ?: return false
        return when (singleStatement) {
            is KtCallExpression -> {
                isConvertibleCallInLambda(
                    callableExpression = singleStatement,
                    lambdaExpression = element
                )
            }
            is KtNameReferenceExpression -> false // Global property reference is not possible (?!)
            is KtDotQualifiedExpression -> {
                val selector = singleStatement.selectorExpression ?: return false
                isConvertibleCallInLambda(
                    callableExpression = selector,
                    explicitReceiver = singleStatement.receiverExpression,
                    lambdaExpression = element
                )
            }
            else -> false
        }
    }

    override fun applyTo(element: KtLambdaExpression, editor: Editor?) {
        val referenceName = buildReferenceText(element) ?: return
        val psiFactory = KtPsiFactory(element.project)
        val parent = element.parent

        val outerCallExpression = parent.getStrictParentOfType<KtCallExpression>()
        val outerCallContext = outerCallExpression?.analyze(BodyResolveMode.PARTIAL)
        val resolvedOuterCall = outerCallContext?.let { outerCallExpression.getResolvedCall(it) }
        if (parent is KtValueArgument && resolvedOuterCall != null) {
            outerCallExpression.addTypeArgumentsIfNeeded(element, parent, resolvedOuterCall, outerCallContext)
        }

        val lambdaArgument = element.parentValueArgument() as? KtLambdaArgument
        if (lambdaArgument == null) {
            if (parent is KtProperty && parent.typeReference == null) {
                val propertyType = parent.descriptor.safeAs<VariableDescriptor>()?.type
                val functionDescriptor = element.singleStatementOrNull()?.resolveToCall()?.resultingDescriptor as? FunctionDescriptor
                if (propertyType != null && functionDescriptor != null && functionDescriptor.overloadedFunctions().size > 1) {
                    parent.setType(propertyType)
                }
            }
            // Without lambda argument syntax, just replace lambda with reference
            val callableReferenceExpr = psiFactory.createCallableReferenceExpression(referenceName) ?: return
            (element.replace(callableReferenceExpr) as? KtElement)?.let { ShortenReferences.RETAIN_COMPANION.process(it) }
        } else {
            // Otherwise, replace the whole argument list for lambda argument-using call
            val outerCalleeDescriptor = resolvedOuterCall?.resultingDescriptor ?: return
            // Parameters with default value
            val valueParameters = outerCalleeDescriptor.valueParameters
            val arguments = outerCallExpression.valueArguments.filter { it !is KtLambdaArgument }
            val hadDefaultValues = valueParameters.size - 1 > arguments.size
            val useNamedArguments = valueParameters.any { it.hasDefaultValue() } && hadDefaultValues
                    || arguments.any { it.isNamed() }

            val newArgumentList = psiFactory.buildValueArgumentList {
                appendFixedText("(")
                arguments.forEach { argument ->
                    val argumentName = argument.getArgumentName()
                    if (useNamedArguments && argumentName != null) {
                        appendName(argumentName.asName)
                        appendFixedText(" = ")
                    }
                    appendExpression(argument.getArgumentExpression())
                    appendFixedText(", ")
                }
                if (useNamedArguments) {
                    appendName(valueParameters.last().name)
                    appendFixedText(" = ")
                }
                appendNonFormattedText(referenceName)
                appendFixedText(")")
            }
            val argumentList = outerCallExpression.valueArgumentList
            if (argumentList == null) {
                (lambdaArgument.replace(newArgumentList) as? KtElement)?.let { ShortenReferences.RETAIN_COMPANION.process(it) }
            } else {
                (argumentList.replace(newArgumentList) as? KtValueArgumentList)?.let {
                    ShortenReferences.RETAIN_COMPANION.process(it.arguments.last())
                }
                lambdaArgument.delete()
            }

            outerCallExpression.typeArgumentList?.let {
                if (RemoveExplicitTypeArgumentsIntention.isApplicableTo(it, approximateFlexible = false)) {
                    it.delete()
                }
            }
        }
    }

    open fun buildReferenceText(lambdaExpression: KtLambdaExpression): String? {
        val lambdaParameterType = lambdaExpression.lambdaParameterType()
        return when (val singleStatement = lambdaExpression.singleStatementOrNull()) {
            is KtCallExpression -> {
                val calleeReferenceExpression = singleStatement.calleeExpression as? KtNameReferenceExpression ?: return null
                val context = calleeReferenceExpression.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)
                val resolvedCall = calleeReferenceExpression.getResolvedCall(context) ?: return null
                val receiver = resolvedCall.dispatchReceiver ?: resolvedCall.extensionReceiver
                val descriptor by lazy { receiver?.type?.constructor?.declarationDescriptor }
                val receiverText = when {
                    lambdaParameterType?.isExtensionFunctionType == true ->
                        calleeReferenceExpression.renderTargetReceiverType(context, resolvedCall)

                    receiver == null || descriptor?.isCompanionObject() == true ||
                            lambdaExpression.languageVersionSettings.languageVersion >= LanguageVersion.KOTLIN_1_2 -> ""

                    receiver is ExtensionReceiver ||
                            descriptor?.let { DescriptorUtils.isAnonymousObject(it) } == true ||
                            lambdaExpression.getResolutionScope().getImplicitReceiversHierarchy().size == 1 -> "this"

                    else -> descriptor?.name?.let { "this@$it" }
                } ?: return null
                val selectorText = singleStatement.getCallReferencedName() ?: return null
                buildReferenceText(receiverText, selectorText, resolvedCall)
            }
            is KtDotQualifiedExpression -> {
                val (selectorReference, selectorReferenceName) = when (val selector = singleStatement.selectorExpression) {
                    is KtCallExpression -> {
                        val callee = selector.calleeExpression as? KtNameReferenceExpression ?: return null
                        callee to callee.getSafeReferencedName()
                    }
                    is KtNameReferenceExpression -> {
                        selector to selector.getSafeReferencedName()
                    }
                    else -> return null
                }
                val receiver = singleStatement.receiverExpression
                val context = receiver.safeAnalyzeNonSourceRootCode()
                val resolvedCall = singleStatement.selectorExpression.getResolvedCall(context)
                when (receiver) {
                    is KtNameReferenceExpression -> {
                        val receiverDescriptor = context[REFERENCE_TARGET, receiver] ?: return null
                        val lambdaValueParameters = context[FUNCTION, lambdaExpression.functionLiteral]?.valueParameters ?: return null
                        if (receiverDescriptor is ParameterDescriptor && receiverDescriptor == lambdaValueParameters.firstOrNull()) {
                            val originalReceiverType = receiverDescriptor.type
                            val receiverType = originalReceiverType.approximateFlexibleTypes(preferNotNull = true)
                            val receiverText = IdeDescriptorRenderers.SOURCE_CODE.renderType(receiverType)
                            buildReferenceText(receiverText, selectorReferenceName, resolvedCall)
                        } else {
                            val receiverName = receiverDescriptor.importableFqName?.asString() ?: receiverDescriptor.name.asString()
                            buildReferenceText(receiverName, selectorReferenceName, resolvedCall)
                        }
                    }
                    else -> {
                        val receiverText = if (lambdaParameterType?.isExtensionFunctionType == true) {
                            selectorReference.renderTargetReceiverType(context)
                        } else {
                            receiver.text
                        } ?: return null
                        buildReferenceText(receiverText, selectorReferenceName, resolvedCall)
                    }
                }
            }
            else -> null
        }
    }

    private fun buildReferenceText(receiver: String, selector: String, resolvedCall: ResolvedCall<out CallableDescriptor>?): String {
        val isVariableCall = resolvedCall is VariableAsFunctionResolvedCall
        val invokeReference = if (resolvedCall?.resultingDescriptor?.isInvokeOperator == true) "::invoke" else ""
        return if (receiver.isEmpty()) {
            val operator = if (isVariableCall) "" else "::"
            "$operator$selector$invokeReference"
        } else {
            val operator = if (isVariableCall) "." else "::"
            "$receiver$operator$selector$invokeReference"
        }
    }

    private fun KtCallExpression.addTypeArgumentsIfNeeded(
        lambda: KtLambdaExpression,
        valueArgument: KtValueArgument,
        resolvedCall: ResolvedCall<out CallableDescriptor>,
        context: BindingContext,
    ) {
        val parameter = resolvedCall.getParameterForArgument(valueArgument) ?: return
        val parameterType = if (parameter.isVararg) {
            parameter.original.type.arguments.firstOrNull()?.type
        } else {
            parameter.original.type
        } ?: return
        if (parameterType.arguments.none { it.type.isTypeParameter() }) return

        val calledFunctionInLambda = lambda.singleStatementOrNull()
            ?.getResolvedCall(context)?.resultingDescriptor as? FunctionDescriptor ?: return
        val overloadedFunctions = calledFunctionInLambda.overloadedFunctions()
        if (overloadedFunctions.count { it.valueParameters.size == calledFunctionInLambda.valueParameters.size } < 2
            && calledFunctionInLambda.typeParameters.isEmpty()
        ) return

        if (InsertExplicitTypeArgumentsIntention.isApplicableTo(this, context)) {
            InsertExplicitTypeArgumentsIntention.applyTo(this)
        }
    }

    private fun FunctionDescriptor.overloadedFunctions(): Collection<SimpleFunctionDescriptor> {
        val memberScope = when (val containingDeclaration = this.containingDeclaration) {
            is ClassDescriptor -> containingDeclaration.unsubstitutedMemberScope
            is LazyPackageDescriptor -> containingDeclaration.getMemberScope()
            else -> null
        }
        return memberScope?.getContributedFunctions(name, NoLookupLocation.FROM_IDE).orEmpty()
    }

    companion object {
        private fun KtLambdaExpression.lambdaParameterType(context: BindingContext? = null): KotlinType? {
            val argument = parentValueArgument() ?: return null
            val callExpression = argument.getStrictParentOfType<KtCallExpression>() ?: return null
            return callExpression
                .getResolvedCall(context ?: safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL))
                ?.getParameterForArgument(argument)?.type
        }

        private fun KtLambdaExpression.parentValueArgument(): KtValueArgument? {
            return if (parent is KtLabeledExpression) {
                parent.parent
            } else {
                parent
            } as? KtValueArgument
        }

        private fun KtCallExpression.getCallReferencedName() = (calleeExpression as? KtNameReferenceExpression)?.getSafeReferencedName()

        private fun KtNameReferenceExpression.getSafeReferencedName() = getReferencedNameAsName().render()

        private fun KtLambdaExpression.singleStatementOrNull() = bodyExpression?.statements?.singleOrNull()

        fun KtLambdaExpression.isArgument() =
            this === getStrictParentOfType<KtValueArgument>()?.getArgumentExpression()?.deparenthesize()

        private fun KtNameReferenceExpression.renderTargetReceiverType(
            context: BindingContext,
            resolvedCall: ResolvedCall<out CallableDescriptor>? = null
        ): String {
            val receiverType = (context[REFERENCE_TARGET, this] as? CallableDescriptor)?.receiverType() ?: return ""
            if (receiverType !is AbbreviatedType) {
                return IdeDescriptorRenderers.SOURCE_CODE.renderType(receiverType)
            }
            val fqName = receiverType.fqName?.asString() ?: return ""
            val typeParameter = (resolvedCall ?: getResolvedCall(context))?.renderTypeParameters() ?: return ""
            return "$fqName$typeParameter"
        }

        private fun ResolvedCall<out CallableDescriptor>.renderTypeParameters(): String {
            val typeArguments = this.typeArguments
            val typeParameters = this.candidateDescriptor.typeParameters.mapNotNull { typeArguments[it] }
            return if (typeParameters.isNotEmpty()) {
                typeParameters.joinToString(prefix = "<", postfix = ">") { t -> IdeDescriptorRenderers.SOURCE_CODE.renderType(t) }
            } else {
                ""
            }
        }
    }
}
