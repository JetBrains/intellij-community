// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.TypeConstraints
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue
import com.intellij.psi.JavaPsiFacade
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor.KotlinExpressionAnchor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull

class KotlinFunctionCallInstruction(
    private val call: KtExpression,
    private val argCount: Int,
    private val qualifierOnStack: Boolean = false,
    private val exceptionTransfer: DfaControlTransferValue?
) :
    ExpressionPushingInstruction(KotlinExpressionAnchor(call)) {
    override fun accept(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState): Array<DfaInstructionState> {
        repeat(argCount) { stateBefore.pop() }
        if (qualifierOnStack) {
            stateBefore.pop()
        }
        stateBefore.flushFields()
        val result = mutableListOf<DfaInstructionState>()
        val type = getExpressionDfType(call)
        if (exceptionTransfer != null) {
            val exceptional = stateBefore.createCopy()
            result += exceptionTransfer.dispatch(exceptional, interpreter)
        }
        if (type != DfType.BOTTOM) {
            pushResult(interpreter, stateBefore, type)
            result += nextState(interpreter, stateBefore)
        }
        return result.toTypedArray()
    }

    private fun getExpressionDfType(expr: KtExpression): DfType {
        val constructedClassName = (expr.resolveToCall()?.resultingDescriptor as? ConstructorDescriptor)?.constructedClass?.fqNameOrNull()
        if (constructedClassName != null) {
            // Set exact class type for constructor
            val psiClass = JavaPsiFacade.getInstance(expr.project).findClass(constructedClassName.asString(), expr.resolveScope)
            if (psiClass != null) {
                return TypeConstraints.exactClass(psiClass).asDfType().meet(DfTypes.NOT_NULL_OBJECT)
            }
        }
        return expr.getKotlinType().toDfType(expr)
    }

    override fun toString(): String {
        return "CALL " + call.text
    }
}