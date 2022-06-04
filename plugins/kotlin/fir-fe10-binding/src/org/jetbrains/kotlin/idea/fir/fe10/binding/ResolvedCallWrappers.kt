// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.fe10.binding

import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.impl.FirNoReceiverExpression
import org.jetbrains.kotlin.fir.realPsi
import org.jetbrains.kotlin.fir.references.FirErrorNamedReference
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.resolvedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.idea.fir.fe10.FE10BindingContext
import org.jetbrains.kotlin.idea.fir.fe10.FirWeakReference
import org.jetbrains.kotlin.idea.fir.fe10.toDeclarationDescriptor
import org.jetbrains.kotlin.idea.fir.fe10.toKotlinType
import org.jetbrains.kotlin.psi.Call
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.results.ResolutionStatus
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.KotlinType


internal abstract class Fe10WrapperResolvedCall<C: Fe10WrapperCall<F>, F: FirQualifiedAccessExpression>(
    private val call: C,
) : ResolvedCall<CallableDescriptor> {
    protected val context: FE10BindingContext get() = call.context
    protected val firAccessExpression: FirWeakReference<F> get() = call.firAccessExpression

    override fun getCall(): Call = call

    override fun getExtensionReceiver(): ReceiverValue? {
        if (firAccessExpression.getFir().extensionReceiver === FirNoReceiverExpression) return null

        return firAccessExpression.getFir().extensionReceiver.toExpressionReceiverValue(context)
    }

    override fun getDispatchReceiver(): ReceiverValue? {
        if (firAccessExpression.getFir().dispatchReceiver === FirNoReceiverExpression) return null

        return firAccessExpression.getFir().dispatchReceiver.toExpressionReceiverValue(context)
    }

    override fun getExplicitReceiverKind(): ExplicitReceiverKind {
        if (firAccessExpression.getFir().explicitReceiver === null) return ExplicitReceiverKind.NO_EXPLICIT_RECEIVER
        if (firAccessExpression.getFir().explicitReceiver === firAccessExpression.getFir().extensionReceiver) return ExplicitReceiverKind.EXTENSION_RECEIVER
        return ExplicitReceiverKind.DISPATCH_RECEIVER
    }

    override fun getContextReceivers(): List<ReceiverValue> = context.implementationPlanned()
    override fun getDataFlowInfoForArguments(): DataFlowInfoForArguments = context.noImplementation()
    override fun getSmartCastDispatchReceiverType(): KotlinType? = context.noImplementation()
}

internal class FunctionFe10WrapperResolvedCall(call: FunctionFe10WrapperCall) :
    Fe10WrapperResolvedCall<FunctionFe10WrapperCall, FirFunctionCall>(call) {

    @OptIn(SymbolInternals::class)
    private val ktFunctionSymbol: KtFunctionLikeSymbol = firAccessExpression.withFir {
        when (val calleeReference = it.calleeReference) {
            is FirResolvedNamedReference -> context.ktAnalysisSessionFacade.buildSymbol(calleeReference.resolvedSymbol.fir) as KtFunctionLikeSymbol

            // TODO: NPE in case of empty candidate
            is FirErrorNamedReference -> context.ktAnalysisSessionFacade.buildSymbol(calleeReference.candidateSymbol!!.fir) as KtFunctionLikeSymbol
            else -> context.noImplementation("calleeReferenceType: ${calleeReference::class.java}")
        }
    }

    private val _typeArguments: Map<TypeParameterDescriptor, KotlinType> by lazy(LazyThreadSafetyMode.PUBLICATION) {

        if (firAccessExpression.getFir().typeArguments.isEmpty()) return@lazy emptyMap()

        val typeArguments = linkedMapOf<TypeParameterDescriptor, KotlinType>()
        for ((index, parameter) in candidateDescriptor.typeParameters.withIndex()) {
            val firTypeProjectionWithVariance = firAccessExpression.getFir().typeArguments[index] as FirTypeProjectionWithVariance
            val kotlinType = context.ktAnalysisSessionFacade.buildKtType(firTypeProjectionWithVariance.typeRef).toKotlinType(context)
            typeArguments[parameter] = kotlinType
        }
        typeArguments
    }

    private val arguments: Map<ValueParameterDescriptor, ResolvedValueArgument> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val firArguments = firAccessExpression.withFir { it.argumentMapping } ?: context.implementationPostponed()

        val firParameterToResolvedValueArgument = hashMapOf<FirValueParameter, ResolvedValueArgument>()
        val allArguments = call.valueArguments + call.functionLiteralArguments
        var argumentIndex = 0
        for ((firExpression, firValueParameter) in firArguments.entries) {
            if (firExpression is FirVarargArgumentsExpression) {
                val varargArguments = mutableListOf<ValueArgument>()
                for (subExpression in firExpression.arguments) {
                    val currentArgument = allArguments[argumentIndex]; argumentIndex++
                    check(currentArgument.getArgumentExpression() === subExpression.realPsi) {
                        "Different psi: ${currentArgument.getArgumentExpression()} !== ${subExpression.realPsi}"
                    }
                    varargArguments.add(currentArgument)
                }
                firParameterToResolvedValueArgument[firValueParameter] = VarargValueArgument(varargArguments)
            } else {
                val currentArgument = allArguments[argumentIndex]; argumentIndex++
                check(currentArgument.getArgumentExpression() === firExpression.realPsi) {
                    "Different psi: ${currentArgument.getArgumentExpression()} !== ${firExpression.realPsi}"
                }
                firParameterToResolvedValueArgument[firValueParameter] = ExpressionValueArgument(currentArgument)
            }
        }

        val arguments = linkedMapOf<ValueParameterDescriptor, ResolvedValueArgument>()
        for ((parameterIndex, parameter) in ktFunctionSymbol.valueParameters.withIndex()) {
            val resolvedValueArgument = context.ktAnalysisSessionFacade.withFir(parameter) { it: FirValueParameter ->
                firParameterToResolvedValueArgument[it]
            } ?: DefaultValueArgument.DEFAULT
            arguments[candidateDescriptor.valueParameters[parameterIndex]] = resolvedValueArgument
        }
        arguments
    }

    override fun getStatus(): ResolutionStatus =
        if (firAccessExpression.getFir().calleeReference is FirResolvedNamedReference) ResolutionStatus.SUCCESS else ResolutionStatus.OTHER_ERROR


    override fun getCandidateDescriptor(): CallableDescriptor {
        return ktFunctionSymbol.toDeclarationDescriptor(context)
    }

    // type arguments should be substituted
    override fun getResultingDescriptor(): CallableDescriptor = context.incorrectImplementation { candidateDescriptor }

    override fun getValueArguments(): Map<ValueParameterDescriptor, ResolvedValueArgument> = arguments

    override fun getValueArgumentsByIndex(): List<ResolvedValueArgument> = valueArguments.values.toList()

    override fun getArgumentMapping(valueArgument: ValueArgument): ArgumentMapping {
        val firArguments = firAccessExpression.withFir { it.argumentMapping } ?: context.implementationPostponed()
        val argumentExpression = valueArgument.getArgumentExpression() ?: context.implementationPostponed()

        fun FirExpression.isMyArgument() = realPsi === valueArgument || realPsi === argumentExpression

        var targetFirParameter: FirValueParameter? = null
        outer@ for ((firExpression, firValueParameter) in firArguments.entries) {
            if (firExpression is FirVarargArgumentsExpression) {
                for (subExpression in firExpression.arguments)
                    if (subExpression.isMyArgument()) {
                        targetFirParameter = firValueParameter
                        break@outer
                    }
            } else if (firExpression.isMyArgument()) {
                targetFirParameter = firValueParameter
                break@outer
            }
        }
        if (targetFirParameter == null) return ArgumentUnmapped

        val parameterIndex = ktFunctionSymbol.valueParameters.indexOfFirst {
            context.ktAnalysisSessionFacade.withFir(it) { parameter: FirValueParameter -> parameter === targetFirParameter }
        }
        if (parameterIndex == -1) error("Fir parameter not found :(")

        val parameterDescriptor = candidateDescriptor.valueParameters[parameterIndex]
        val argumentMatch = ArgumentMatchImpl(parameterDescriptor)
        context.incorrectImplementation {
            // I'm not sure, when we should have not success status
            argumentMatch.recordMatchStatus(ArgumentMatchStatus.SUCCESS)
        }
        return argumentMatch
    }

    override fun getTypeArguments(): Map<TypeParameterDescriptor, KotlinType> = _typeArguments
}

internal class VariableFe10WrapperResolvedCall(call: VariableFe10WrapperCall) :
    Fe10WrapperResolvedCall<VariableFe10WrapperCall, FirPropertyAccessExpression>(call) {

    @OptIn(SymbolInternals::class)
    private val ktVariableSymbol: KtVariableLikeSymbol = firAccessExpression.withFir {
        val resolvedSymbol = it.calleeReference.resolvedSymbol
        check(resolvedSymbol is FirVariableSymbol) {
            "Expected FirVariableSymbol, but get: $resolvedSymbol"
        }

        context.ktAnalysisSessionFacade.buildSymbol(resolvedSymbol.fir) as KtVariableLikeSymbol
    }


    override fun getStatus(): ResolutionStatus = context.incorrectImplementation { ResolutionStatus.SUCCESS }

    override fun getCandidateDescriptor(): CallableDescriptor = ktVariableSymbol.toDeclarationDescriptor(context) as CallableDescriptor

    // type arguments should be substituted
    override fun getResultingDescriptor(): CallableDescriptor = context.incorrectImplementation { candidateDescriptor }

    override fun getValueArguments(): Map<ValueParameterDescriptor, ResolvedValueArgument> = emptyMap()
    override fun getValueArgumentsByIndex(): List<ResolvedValueArgument> = emptyList()
    override fun getArgumentMapping(valueArgument: ValueArgument): ArgumentMapping =
        error("Variable call has no arguments. fir: $firAccessExpression, valueArgument = $valueArgument")

    override fun getTypeArguments(): Map<TypeParameterDescriptor, KotlinType> = emptyMap()
}