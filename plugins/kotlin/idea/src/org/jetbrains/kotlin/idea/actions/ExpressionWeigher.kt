// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyze
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinAutoImportCallableWeigher
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.inspections.dfa.getArrayElementType
import org.jetbrains.kotlin.idea.intentions.receiverType
import org.jetbrains.kotlin.idea.quickfix.ImportFixHelper
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections

/**
 * Implementation in K2: [org.jetbrains.kotlin.idea.quickfix.importFix.ExpressionImportWeigher]
 */
internal interface ExpressionWeigher {

    fun weigh(descriptor: DeclarationDescriptor): Int

    companion object {
        fun createWeigher(element: PsiElement?): ExpressionWeigher =
            when (element) {
                is KtNameReferenceExpression -> CallExpressionWeigher(element)
                is KtOperationReferenceExpression -> OperatorExpressionWeigher(element)
                else -> EmptyExpressionWeigher
            }
    }
}

internal object EmptyExpressionWeigher: ExpressionWeigher {
    override fun weigh(descriptor: DeclarationDescriptor): Int = 0

}

internal abstract class AbstractExpressionWeigher: ExpressionWeigher {
    override fun weigh(descriptor: DeclarationDescriptor): Int {
        val base = descriptor.importableFqName?.let { fqName ->
            ImportFixHelper.calculateWeightBasedOnFqName(fqName, (descriptor as? DeclarationDescriptorWithSource)?.source?.getPsi())
        } ?: 0

        return base + ownWeigh(descriptor)
    }

    protected abstract fun ownWeigh(descriptor: DeclarationDescriptor): Int

    protected fun KotlinType.weight(weight: Int, kotlinType: KotlinType?): Int? {
        if (kotlinType == null) return null
        val typeMarkedNullable = kotlinType.isMarkedNullable
        val markedNullable = isMarkedNullable

        val adjustedType: KotlinType
        val nullablesWeight = if (typeMarkedNullable == markedNullable) {
            adjustedType = this
            2
        } else {
            adjustedType = if (markedNullable) makeNotNullable() else this
            // no reason to make `kotlinType` not nullable as `T` is a subtype of `T?`
            0
        }

        return if (KotlinTypeChecker.DEFAULT.isSubtypeOf(adjustedType, kotlinType)) {
            100 * weight + 10 + nullablesWeight
        } else {
            null
        }
    }

}

internal class CallExpressionWeigher(private val element: KtNameReferenceExpression): AbstractExpressionWeigher() {

    private val argumentKotlinTypes: List<KotlinType>
    private val valueArgumentsSize: Int
    private val receiverType: KotlinType?

    init {
        val callExpression = element?.getParentOfType<KtCallElement>(false)
        val receiverExpression = element?.getParentOfType<KtQualifiedExpression>(false)?.receiverExpression ?: element?.getParentOfType<KtLambdaExpression>(false)
        receiverType = if (receiverExpression != null) {
            val context = receiverExpression.analyze(BodyResolveMode.PARTIAL)
            val type = if (receiverExpression is KtLambdaExpression) {
                val functionDescriptor = context[BindingContext.FUNCTION, receiverExpression.functionLiteral]
                functionDescriptor?.extensionReceiverParameter?.type ?: receiverExpression.getParentOfType<KtClassOrObject>(false)
                    ?.resolveToDescriptorIfAny()?.defaultType
            } else {
                receiverExpression.getType(context)
            }
            // use non-nullable type if safe call is used i.e `val value: T? = ...; value?.smth()`
            if (receiverExpression.parent is KtSafeQualifiedExpression) {
                type?.makeNotNullable()
            } else {
                type
            }
        } else {
            null
        }

        val valueArgumentList = callExpression?.valueArgumentList
        argumentKotlinTypes = if (callExpression != null && valueArgumentList != null) {
            val valueArguments = callExpression.valueArguments
            valueArgumentsSize = valueArguments.size

            val types = ArrayList<KotlinType>(valueArgumentsSize)
            val bindingContext = valueArgumentList.analyze(BodyResolveMode.PARTIAL)

            for (valueArgument in valueArguments) {
                val argumentExpression = valueArgument.getArgumentExpression() ?: break
                types += argumentExpression.getType(bindingContext) ?: break
            }
            types
        } else {
            valueArgumentsSize = 0
            emptyList()
        }
    }

    override fun ownWeigh(descriptor: DeclarationDescriptor): Int =
        when (descriptor) {
            is CallableMemberDescriptor -> calculateWeight(descriptor, argumentKotlinTypes)
            is ClassDescriptor -> calculateWeight(descriptor, argumentKotlinTypes)
            else -> 0
        }

    private fun calculateWeight(
        callableMemberDescriptor: CallableMemberDescriptor?,
        kotlinTypes: List<KotlinType>
    ): Int {
        if (callableMemberDescriptor == null) return 0

        var weight = 0
        receiverType?.let {
            val receiverValueType = callableMemberDescriptor.extensionReceiverParameter?.value?.type
            weight = it.weight(weight, receiverValueType) ?: weight
        }
        val valueParameters = callableMemberDescriptor.valueParameters
        val descriptorParameters = valueParameters.size
        val descriptorHasVarargParameter = valueParameters.any { it?.isVararg == true }

        weight += if (descriptorParameters >= valueArgumentsSize || descriptorHasVarargParameter) {
            // same number of arguments is better than when more arguments
            if (descriptorParameters == valueArgumentsSize || descriptorHasVarargParameter) 1 else 0
        } else {
            // apply only base weigh if target has fewer parameters than expected
            return weight
        }

        // apply weighing extensions
        val function = DescriptorToSourceUtilsIde.getAnyDeclaration(element.project, callableMemberDescriptor) as? KtFunction
        if (function != null) {
            weight += calculateCallExtensionsWeight(function)
        }

        val valueParameterDescriptorIterator: MutableIterator<ValueParameterDescriptor> = valueParameters.iterator()
        var valueParameterDescriptor: ValueParameterDescriptor? = null

        // TODO: it does not cover following cases:
        //  - named parameters
        //  - default value, e.g. `param: Int = ""`
        //  - functional types, e.g. `Int.() -> Unit`
        //  - functional references, e.g. `::foo`

        for (kotlinType in kotlinTypes) {
            if (!valueParameterDescriptorIterator.hasNext()) {
                break
            }
            if (valueParameterDescriptor == null || !valueParameterDescriptor.isVararg) {
                // vararg could be only the last parameter, there is no parameters left
                valueParameterDescriptor = valueParameterDescriptorIterator.next()
            }

            // replace `<T>` but `<*>` if needed, otherwise `<T>` has no subtypes
            val returnType = valueParameterDescriptor.returnType?.replaceArgumentsWithStarProjections()
            val vararg = valueParameterDescriptor.isVararg
            val valueParameterType = if (vararg) {
                // `vararg a: Int` has type `IntArray`
                returnType?.getArrayElementType()
            } else {
                returnType
            }

            weight = kotlinType.weight(weight, valueParameterType) ?: kotlinType.weight(weight, returnType) ?: break
        }
        return weight
    }

    private fun calculateWeight(descriptor: ClassDescriptor, kotlinTypes: List<KotlinType>): Int {
        // TODO: some constructors could be not visible
        descriptor.constructors
            // Filter out default constructors that don't have their own PSI.
            .filter { DescriptorToSourceUtilsIde.getAnyDeclaration(element.project, it) is KtFunction }
            .maxOfOrNull { calculateWeight(it, kotlinTypes) }?.let { return it }

        // In some cases (eg with an interface), there are no constructors. Weigh the class instead.
        val classOrObject =
            DescriptorToSourceUtilsIde.getAnyDeclaration(element.project, descriptor) as? KtClassOrObject
                ?: return 0

        return calculateCallExtensionsWeight(classOrObject)
    }

    /**
     * Since [ExpressionWeigher]s are executed on EDT in K1 plugin, we have to use [allowAnalysisOnEdt] to be able to perform the [analyze].
     * With K2 plugin, there will be no such issue.
     */
    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun calculateCallExtensionsWeight(function: KtFunction): Int {
        return allowAnalysisOnEdt {
            analyze(element) {
                val callableSymbol = function.symbol as? KaCallableSymbol
                if (callableSymbol != null) {
                    with(KotlinAutoImportCallableWeigher) { weigh(callableSymbol, element) }
                } else {
                    0
                }
            }
        }
    }

    /**
     * Since [ExpressionWeigher]s are executed on EDT in K1 plugin, we have to use [allowAnalysisOnEdt] to be able to perform the [analyze].
     * With K2 plugin, there will be no such issue.
     */
    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun calculateCallExtensionsWeight(classOrObject: KtClassOrObject): Int {
        return allowAnalysisOnEdt {
            analyze(element) {
                val symbol = classOrObject.symbol as? KaClassSymbol
                if (symbol != null) {
                    with(KotlinAutoImportCallableWeigher) { weigh(symbol, element) }
                } else {
                    0
                }
            }
        }
    }
}

internal class OperatorExpressionWeigher(element: KtOperationReferenceExpression): AbstractExpressionWeigher() {

    private val leftType: KotlinType?
    private val rightType: KotlinType?
    private val operatorName: Name?

    init {
        operatorName = element.operationSignTokenType?.let { operationSignTokenType ->
            OperatorConventions.getNameForOperationSymbol(operationSignTokenType, false, true)
        }
        val parent = element.parent
        if (parent is KtBinaryExpression) {
            val context = parent.safeAnalyze(BodyResolveMode.PARTIAL)
            leftType = parent.left?.getType(context)
            rightType = parent.right?.getType(context)
        } else {
            leftType = null
            rightType = null
        }
    }

    override fun weigh(descriptor: DeclarationDescriptor): Int {
        val functionDescriptor = (descriptor as? FunctionDescriptor)?.takeIf { it.isOperator } ?: return 0

        return super.weigh(functionDescriptor)
    }

    override fun ownWeigh(descriptor: DeclarationDescriptor): Int {
        val functionDescriptor = descriptor as FunctionDescriptor

        val name = functionDescriptor.name
        var weight = 0
        if (name == operatorName) {
            weight += 8
        }
        (descriptor as? CallableDescriptor)?.let {
            val receiverType = it.receiverType()?.replaceArgumentsWithStarProjections()
            weight = leftType?.weight(weight, receiverType) ?: weight

            val valueParameterDescriptor = it.valueParameters.firstOrNull()
            val valueParameterType = valueParameterDescriptor?.returnType?.replaceArgumentsWithStarProjections()
            if (valueParameterType != null) {
                weight = rightType?.weight(weight, valueParameterType) ?: weight
            }
        }

        return weight
    }

}