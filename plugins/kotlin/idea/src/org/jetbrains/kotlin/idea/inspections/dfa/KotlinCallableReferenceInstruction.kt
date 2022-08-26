// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression

/**
 * Instruction to process Kotlin callable reference, assuming the qualifier is on the stack
 * (or unknown value if there's no qualifier)
 */
class KotlinCallableReferenceInstruction(val expr: KtCallableReferenceExpression):
    ExpressionPushingInstruction(KotlinAnchor.KotlinExpressionAnchor(expr)) {
    override fun accept(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState): Array<DfaInstructionState> {
        val qualifier = stateBefore.pop()
        JavaDfaHelpers.dropLocality(qualifier, stateBefore)
        val dfType = expr.getKotlinType().toDfType()
        pushResult(interpreter, stateBefore, dfType)
        return nextStates(interpreter, stateBefore)
    }

    override fun toString(): String {
        return "CALLABLE_REFERENCE"
    }
}