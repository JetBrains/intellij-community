// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor.KotlinExpressionAnchor
import org.jetbrains.kotlin.psi.KtExpression

/**
 * Instruction that processes kotlin == or != operators between unknown reference values, assuming the following contract:
 * if x == y => true
 * else if x == null => false
 * else if y == null => false
 * else unknown
 */
class KotlinEqualityInstruction(
    equality: KtExpression,
    private val negated: Boolean,
    private val exceptionTransfer: DfaControlTransferValue?
) : ExpressionPushingInstruction(KotlinExpressionAnchor(equality)) {

    override fun bindToFactory(factory: DfaValueFactory): Instruction =
        if (exceptionTransfer == null) this
        else KotlinEqualityInstruction((dfaAnchor as KotlinExpressionAnchor).expression, negated, exceptionTransfer.bindToFactory(factory))

    override fun accept(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState): Array<DfaInstructionState> {
        val right = stateBefore.pop()
        val left = stateBefore.pop()
        val result = mutableListOf<DfaInstructionState>()
        if (exceptionTransfer != null) {
            val exceptional = stateBefore.createCopy()
            result += exceptionTransfer.dispatch(exceptional, interpreter)
        }
        val eqState = stateBefore.createCopy()
        val leftEqRight = left.eq(right)
        if (eqState.applyCondition(leftEqRight)) {
            pushResult(interpreter, eqState, DfTypes.booleanValue(!negated))
            result += nextState(interpreter, eqState)
        }
        if (stateBefore.applyCondition(leftEqRight.negate())) {
            val leftNullState = stateBefore.createCopy()
            val nullValue = interpreter.factory.fromDfType(DfTypes.NULL)
            val leftEqNull = left.eq(nullValue)
            if (leftNullState.applyCondition(leftEqNull)) {
                pushResult(interpreter, leftNullState, DfTypes.booleanValue(negated))
                result += nextState(interpreter, leftNullState)
            }
            if (stateBefore.applyCondition(leftEqNull.negate())) {
                val rightNullState = stateBefore.createCopy()
                val rightEqNull = right.eq(nullValue)
                if (rightNullState.applyCondition(rightEqNull)) {
                    pushResult(interpreter, rightNullState, DfTypes.booleanValue(negated))
                    result += nextState(interpreter, rightNullState)
                }
                if (stateBefore.applyCondition(rightEqNull.negate())) {
                    pushResult(interpreter, stateBefore, DfTypes.BOOLEAN)
                    result += nextState(interpreter, stateBefore)
                }
            }
        }
        return result.toTypedArray()
    }

    override fun toString(): String {
        return if (negated) "NOT_EQUAL" else "EQUAL"
    }
}