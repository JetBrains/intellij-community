// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa

import com.intellij.codeInspection.dataFlow.*
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers
import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil
import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet
import com.intellij.codeInspection.dataFlow.types.DfJvmIntegralType
import com.intellij.codeInspection.dataFlow.types.DfReferenceType
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.*
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.contracts.description.*
import org.jetbrains.kotlin.analysis.api.contracts.description.KaContractReturnsContractEffectDeclaration.*
import org.jetbrains.kotlin.analysis.api.contracts.description.booleans.*
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor.KotlinExpressionAnchor
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KtClassDef.Companion.classDef
import org.jetbrains.kotlin.psi.KtExpression

// TODO: support Java contracts
class KotlinFunctionCallInstruction(
    private val call: KtExpression,
    private val argCount: Int,
    private val qualifierOnStack: Boolean = false,
    private val exceptionTransfer: DfaControlTransferValue?
) : ExpressionPushingInstruction(KotlinExpressionAnchor(call)) {

    override fun bindToFactory(factory: DfaValueFactory): Instruction =
        if (exceptionTransfer == null) this
        else KotlinFunctionCallInstruction(
            (dfaAnchor as KotlinExpressionAnchor).expression, argCount,
            qualifierOnStack, exceptionTransfer.bindToFactory(factory)
        )

    override fun accept(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState): Array<DfaInstructionState> {
        val arguments = popArguments(stateBefore, interpreter)
        val factory = interpreter.factory
        var (resultValue, pure) = analyze(call) { getMethodReturnValue(factory, stateBefore, arguments) }
        if (!pure || JavaDfaHelpers.mayLeakFromType(resultValue.dfType)) {
            arguments.arguments.forEach { arg -> JavaDfaHelpers.dropLocality(arg, stateBefore) }
            val qualifier = arguments.qualifier
            if (qualifier != null) {
                JavaDfaHelpers.dropLocality(qualifier, stateBefore)
            }
        }
        if (!pure) {
            stateBefore.flushFields()
        }
        val result = mutableListOf<DfaInstructionState>()
        if (exceptionTransfer != null) {
            val exceptional = stateBefore.createCopy()
            result += exceptionTransfer.dispatch(exceptional, interpreter)
        }
        resultValue = analyze(call) { processContracts(interpreter, stateBefore, resultValue, arguments, result) }
        if (resultValue.dfType != DfType.BOTTOM) {
            pushResult(interpreter, stateBefore, resultValue)
            result += nextState(interpreter, stateBefore)
        }
        return result.toTypedArray()
    }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun processContracts(
        interpreter: DataFlowInterpreter,
        stateBefore: DfaMemoryState,
        resultValue: DfaValue,
        arguments: DfaCallArguments,
        result: MutableList<DfaInstructionState>
    ): DfaValue {
        val factory = resultValue.factory
        val functionCall = call.resolveToCall()?.singleFunctionCallOrNull() ?: return resultValue
        val functionSymbol = functionCall.partiallyAppliedSymbol.symbol as? KaNamedFunctionSymbol ?: return resultValue
        val callEffects = functionSymbol.contractEffects
        for (effect in callEffects) {
            if (effect !is KaContractConditionalContractEffectDeclaration) continue
            val crv = effect.effect.toContractReturnValue() ?: continue
            val condition = effect.condition.toCondition(factory, functionCall, arguments) ?: continue
            val notCondition = condition.negate()
            if (notCondition == DfaCondition.getFalse()) continue
            val returnValue = crv.getDfaValue(factory, DfaCallState(stateBefore, arguments, factory.unknown))
            val negated = returnValue.dfType.tryNegate() ?: continue
            val negatedResult = factory.fromDfType(resultValue.dfType.meet(negated))
            if (notCondition == DfaCondition.getTrue()) {
                return negatedResult
            }
            if (negatedResult.dfType != DfType.BOTTOM) {
                val notSatisfiedState = stateBefore.createCopy()
                if (notSatisfiedState.applyCondition(notCondition)) {
                    pushResult(interpreter, notSatisfiedState, negatedResult)
                    result += nextState(interpreter, notSatisfiedState)
                }
            }
            if (!stateBefore.applyCondition(condition)) {
                return factory.fromDfType(DfType.BOTTOM)
            }
        }
        return resultValue
    }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun KaContractBooleanExpression.toCondition(
        factory: DfaValueFactory,
        callDescriptor: KaFunctionCall<*>,
        arguments: DfaCallArguments
    ): DfaCondition? {
        return when (this) {
            is KaContractBooleanConstantExpression -> if (booleanConstant) DfaCondition.getTrue() else DfaCondition.getFalse()
            is KaContractBooleanValueParameterExpression -> {
                parameterSymbol.findDfaValue(callDescriptor, arguments)?.cond(RelationType.EQ, factory.fromDfType(DfTypes.TRUE))
            }

            is KaContractLogicalNotExpression -> argument.toCondition(factory, callDescriptor, arguments)?.negate()
            is KaContractIsNullPredicateExpression -> argument.findDfaValue(callDescriptor, arguments)
                ?.cond(RelationType.equivalence(!isNegated), factory.fromDfType(DfTypes.NULL))

            is KaContractIsInstancePredicateExpression -> argument.findDfaValue(callDescriptor, arguments)
                ?.cond(if (isNegated) RelationType.IS_NOT else RelationType.IS, factory.fromDfType(type.toDfType()))

            else -> null
        }
    }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun KaContractParameterValue.findDfaValue(
        callDescriptor: KaFunctionCall<*>,
        arguments: DfaCallArguments
    ): DfaValue? = when (this) {
        is KaContractExplicitParameterValue -> when (val symbol = symbol) {
            // TODO: KTIJ-33109 support context parameters
            is KaContextParameterSymbol -> null
            is KaValueParameterSymbol -> {
                val parameterIndex = callDescriptor.argumentMapping.values.map { it.symbol }.indexOf(symbol)
                if (parameterIndex >= 0 && parameterIndex < arguments.arguments.size) {
                    arguments.arguments[parameterIndex]
                } else {
                    null
                }
            }

            is KaReceiverParameterSymbol -> arguments.qualifier
        }

        is KaContractOwnerParameterValue -> arguments.qualifier
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaContractEffectDeclaration.toContractReturnValue(): ContractReturnValue? {
        return when (this) {
            is KaContractReturnsNotNullEffectDeclaration -> ContractReturnValue.returnNotNull()
            is KaContractReturnsSuccessfullyEffectDeclaration -> ContractReturnValue.returnAny()
            is KaContractReturnsSpecificValueEffectDeclaration -> when (value.constantType) {
                KaContractConstantValue.KaContractConstantType.FALSE -> ContractReturnValue.returnFalse()
                KaContractConstantValue.KaContractConstantType.TRUE -> ContractReturnValue.returnTrue()
                KaContractConstantValue.KaContractConstantType.NULL -> ContractReturnValue.returnNull()
            }

            else -> null
        }
    }

    data class MethodEffect(val dfaValue: DfaValue, val pure: Boolean)

    context(_: KaSession)
    private fun getMethodReturnValue(
        factory: DfaValueFactory,
        stateBefore: DfaMemoryState,
        arguments: DfaCallArguments
    ): MethodEffect {
        val method = getPsiMethod()
        val pure = MutationSignature.fromMethod(method).isPure
        if (method != null && arguments.arguments.size == method.parameterList.parametersCount) {
            val handler = CustomMethodHandlers.find(method)
            if (handler != null) {
                var dfaValue = handler.getMethodResultValue(arguments, stateBefore, factory, method)
                if (dfaValue != null) {
                    val dfReferenceType = (dfaValue as? DfaTypeValue)?.dfType as? DfReferenceType
                    if (dfReferenceType != null) {
                        val newType = dfReferenceType.convert(KtClassDef.typeConstraintFactory(call))
                        dfaValue = factory.fromDfType(newType)
                    }
                    return MethodEffect(dfaValue, pure)
                }
            }
        }
        val functionCall = call.resolveToCall()?.singleFunctionCallOrNull()
        var dfType = getExpressionDfType(call)
        if (functionCall != null) {
            val type = fromKnownDescriptor(functionCall, arguments, stateBefore)
            if (type != null) return MethodEffect(factory.fromDfType(type.meet(dfType)), true)
        }
        if (dfType is DfJvmIntegralType && method != null) {
            val specifiedRange = JvmPsiRangeSetUtil.fromPsiElement(method)
                .meet(JvmPsiRangeSetUtil.typeRange(method.returnType, true) ?: LongRangeSet.all())
            dfType = dfType.meetRange(specifiedRange)
        }
        return MethodEffect(factory.fromDfType(dfType), pure)
    }

    private fun fromKnownDescriptor(call: KaFunctionCall<*>, arguments: DfaCallArguments, state: DfaMemoryState): DfType? {
        val functionSymbol = call.partiallyAppliedSymbol.symbol as? KaNamedFunctionSymbol ?: return null
        val name = functionSymbol.name.asString()
        val containingPackage = functionSymbol.callableId?.packageName?.asString() ?: return null
        if (containingPackage == "kotlin.collections") {
            val args = arguments.arguments
            if (args.size > 1) return null
            val size =
                if (args.isEmpty()) DfTypes.intValue(0)
                else state.getDfType(SpecialField.ARRAY_LENGTH.createValue(args[0].factory, args[0]))
            return when (name) {
                "arrayOf", "booleanArrayOf", "byteArrayOf", "shortArrayOf", "charArrayOf",
                "floatArrayOf", "intArrayOf", "doubleArrayOf", "longArrayOf" ->
                    SpecialField.ARRAY_LENGTH.asDfType(size)

                "emptyList", "emptySet", "emptyMap" ->
                    SpecialField.COLLECTION_SIZE.asDfType(DfTypes.intValue(0))
                        .meet(Mutability.UNMODIFIABLE.asDfType())

                "listOf" ->
                    SpecialField.COLLECTION_SIZE.asDfType(size)
                        .meet(Mutability.UNMODIFIABLE.asDfType())

                "listOfNotNull", "setOfNotNull", "mapOfNotNull" ->
                    SpecialField.COLLECTION_SIZE.asDfType(
                        size.fromRelation(RelationType.LE).meet(DfTypes.intValue(0).fromRelation(RelationType.GE))
                    )
                        .meet(Mutability.UNMODIFIABLE.asDfType())

                "setOf", "mapOf" ->
                    SpecialField.COLLECTION_SIZE.asDfType(size.toSetSize())
                        .meet(Mutability.UNMODIFIABLE.asDfType())

                "mutableListOf", "arrayListOf" ->
                    SpecialField.COLLECTION_SIZE.asDfType(size)
                        .meet(DfTypes.LOCAL_OBJECT)

                "mutableSetOf", "linkedSetOf", "hashSetOf", "hashMapOf", "linkedMapOf" ->
                    SpecialField.COLLECTION_SIZE.asDfType(size.toSetSize()).meet(DfTypes.LOCAL_OBJECT)

                else -> null
            }
        }
        return null
    }

    private fun DfType.toSetSize(): DfType {
        val minValue = if (DfTypes.intValue(0).isSuperType(this)) 0 else 1
        return fromRelation(RelationType.LE).meet(DfTypes.intValue(minValue).fromRelation(RelationType.GE))
    }

    private fun popArguments(stateBefore: DfaMemoryState, interpreter: DataFlowInterpreter): DfaCallArguments {
        val args = mutableListOf<DfaValue>()
        repeat(argCount) { args += stateBefore.pop() }
        args.reverse()
        val qualifier: DfaValue = if (qualifierOnStack) stateBefore.pop() else interpreter.factory.unknown
        return DfaCallArguments(qualifier, args.toTypedArray(), MutationSignature.unknown())
    }

    context(_: KaSession)
    private fun getPsiMethod(): PsiMethod? {
        return call.resolveToCall()?.singleFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol?.psi?.toLightMethods()?.singleOrNull()
    }

    context(_: KaSession)
    private fun getExpressionDfType(expr: KtExpression): DfType {
        val constructedClass = (((expr.resolveToCall() as? KaSuccessCallInfo)
            ?.call as? KaCallableMemberCall<*, *>)
            ?.partiallyAppliedSymbol?.symbol as? KaConstructorSymbol)
            ?.containingDeclaration as? KaClassSymbol
        if (constructedClass != null) {
            // Set exact class type for constructor
            return TypeConstraints.exactClass(constructedClass.classDef())
                .convert(KtClassDef.typeConstraintFactory(expr))
                .asDfType().meet(DfTypes.NOT_NULL_OBJECT)
        }
        return expr.getKotlinType().toDfType()
    }

    override fun getSuccessorIndexes(): IntArray {
        return if (exceptionTransfer == null) intArrayOf(index + 1) else exceptionTransfer.possibleTargetIndices + (index + 1)
    }

    override fun toString(): String {
        return "CALL " + call.text
    }
}