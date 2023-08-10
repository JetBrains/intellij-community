// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.dfa

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
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.contracts.description.*
import org.jetbrains.kotlin.contracts.description.expressions.*
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor.KotlinExpressionAnchor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.source.PsiSourceElement

// TODO: support Java contracts
class KotlinFunctionCallInstruction(
    private val call: KtExpression,
    private val argCount: Int,
    private val qualifierOnStack: Boolean = false,
    private val exceptionTransfer: DfaControlTransferValue?
) : ExpressionPushingInstruction(KotlinExpressionAnchor(call)) {

    override fun bindToFactory(factory: DfaValueFactory): Instruction =
        if (exceptionTransfer == null) this
        else KotlinFunctionCallInstruction((dfaAnchor as KotlinExpressionAnchor).expression, argCount,
                                           qualifierOnStack, exceptionTransfer.bindToFactory(factory))

    override fun accept(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState): Array<DfaInstructionState> {
        val arguments = popArguments(stateBefore, interpreter)
        val factory = interpreter.factory
        var (resultValue, pure) = getMethodReturnValue(factory, stateBefore, arguments)
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
        resultValue = processContracts(interpreter, stateBefore, resultValue, arguments, result)
        if (resultValue.dfType != DfType.BOTTOM) {
            pushResult(interpreter, stateBefore, resultValue)
            result += nextState(interpreter, stateBefore)
        }
        return result.toTypedArray()
    }

    private fun KotlinFunctionCallInstruction.processContracts(
        interpreter: DataFlowInterpreter,
        stateBefore: DfaMemoryState,
        resultValue: DfaValue,
        arguments: DfaCallArguments,
        result: MutableList<DfaInstructionState>
    ): DfaValue {
        val factory = resultValue.factory
        val callDescriptor = call.resolveToCall()?.resultingDescriptor
        if (callDescriptor != null) {
            val contractDescription = callDescriptor.getUserData(ContractProviderKey)?.getContractDescription()
            if (contractDescription != null) {
                for (effect in contractDescription.effects) {
                    if (effect is ConditionalEffectDeclaration) {
                        val crv = effect.effect.toContractReturnValue() ?: continue
                        val condition = effect.condition.toCondition(factory, callDescriptor, arguments) ?: continue
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
                }
            }
        }
        return resultValue
    }

    private fun BooleanExpression.toCondition(
        factory: DfaValueFactory,
        callDescriptor: CallableDescriptor,
        arguments: DfaCallArguments
    ): DfaCondition? {
        return when(this) {
            BooleanConstantReference.TRUE -> DfaCondition.getTrue()
            BooleanConstantReference.FALSE -> DfaCondition.getFalse()
            is BooleanVariableReference -> {
                findDfaValue(callDescriptor, arguments)?.cond(RelationType.EQ, factory.fromDfType(DfTypes.TRUE))
            }
            is LogicalNot -> arg.toCondition(factory, callDescriptor, arguments)?.negate()
            is IsNullPredicate -> arg.findDfaValue(callDescriptor, arguments)?.cond(RelationType.equivalence(!isNegated),
                                                                                    factory.fromDfType(DfTypes.NULL))
            is IsInstancePredicate -> arg.findDfaValue(callDescriptor, arguments)?.cond(if (isNegated) RelationType.IS_NOT else RelationType.IS,
                                                                                        factory.fromDfType(type.toDfType()))
            else -> null
        }
    }

    private fun VariableReference.findDfaValue(
        callDescriptor: CallableDescriptor,
        arguments: DfaCallArguments
    ): DfaValue? {
        if (descriptor is ReceiverParameterDescriptor && descriptor.containingDeclaration == callDescriptor.original) {
            return arguments.qualifier
        }
        val parameterIndex = callDescriptor.original.valueParameters.indexOf(descriptor)
        return if (parameterIndex >= 0 && parameterIndex < arguments.arguments.size) {
            arguments.arguments[parameterIndex]
        } else {
            null
        }
    }

    private fun EffectDeclaration.toContractReturnValue():ContractReturnValue? {
        return when ((this as? ReturnsEffectDeclaration)?.value) {
            ConstantReference.NOT_NULL -> ContractReturnValue.returnNotNull()
            ConstantReference.NULL -> ContractReturnValue.returnNull()
            ConstantReference.WILDCARD -> ContractReturnValue.returnAny()
            BooleanConstantReference.FALSE -> ContractReturnValue.returnFalse()
            BooleanConstantReference.TRUE -> ContractReturnValue.returnTrue()
            else -> null
        }
    }

    data class MethodEffect(val dfaValue: DfaValue, val pure: Boolean)

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
        val descriptor = call.resolveToCall()?.resultingDescriptor
        var dfType = getExpressionDfType(call)
        if (descriptor != null) {
            val type = fromKnownDescriptor(descriptor, arguments, stateBefore)
            if (type != null) return MethodEffect(factory.fromDfType(type.meet(dfType)), true)
        }
        if (dfType is DfJvmIntegralType && method != null) {
            val specifiedRange = JvmPsiRangeSetUtil.fromPsiElement(method)
                .meet(JvmPsiRangeSetUtil.typeRange(method.returnType, true) ?: LongRangeSet.all())
            dfType = dfType.meetRange(specifiedRange)
        }
        return MethodEffect(factory.fromDfType(dfType), pure)
    }

    private fun fromKnownDescriptor(descriptor: CallableDescriptor, arguments: DfaCallArguments, state: DfaMemoryState): DfType? {
        val name = descriptor.name.asString()
        val containingPackage = (descriptor.containingDeclaration as? PackageFragmentDescriptor)?.fqName?.asString() ?: return null
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

    private fun getPsiMethod(): PsiMethod? {
        return when (val source = (call.resolveToCall()?.resultingDescriptor?.source as? PsiSourceElement)?.psi) {
            is KtNamedFunction -> source.toLightMethods().singleOrNull()
            is PsiMethod -> source
            else -> null
        }
    }

    private fun getExpressionDfType(expr: KtExpression): DfType {
        val constructedClass = (expr.resolveToCall()?.resultingDescriptor as? ConstructorDescriptor)?.constructedClass
        if (constructedClass != null) {
            // Set exact class type for constructor
            return TypeConstraints.exactClass(KtClassDef(constructedClass))
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