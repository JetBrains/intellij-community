// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression

/**
 * Instruction to process Kotlin callable reference, assuming the qualifier is on the stack
 * (or unknown value if there's no qualifier)
 */
class KotlinCallableReferenceInstruction(val expr: KtCallableReferenceExpression, private val dfType: DfType):
    ExpressionPushingInstruction(KotlinAnchor.KotlinExpressionAnchor(expr)) {
    override fun accept(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState): Array<DfaInstructionState> {
        val qualifier = stateBefore.pop()
        JavaDfaHelpers.dropLocality(qualifier, stateBefore)
        pushResult(interpreter, stateBefore, dfType)
        return nextStates(interpreter, stateBefore)
    }

    override fun toString(): String {
        return "CALLABLE_REFERENCE"
    }
}