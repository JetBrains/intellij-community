// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.TypeConstraint
import com.intellij.codeInspection.dataFlow.lang.ir.EvalInstruction
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import org.jetbrains.kotlin.types.KotlinType

class KotlinClassToJavaClassInstruction(ktAnchor: KotlinAnchor.KotlinExpressionAnchor,
        private val targetClassType: TypeConstraint): EvalInstruction(ktAnchor, 1) {
    override fun eval(factory: DfaValueFactory, state: DfaMemoryState, vararg arguments: DfaValue): DfaValue {
        val arg = state.getDfType(arguments[0]).getConstantOfType(KotlinType::class.java)
        if (arg != null) {
            val psiType = TypeConstraint.fromDfType(arg.toDfType()).getPsiType(factory.project)
            if (psiType != null) {
                return factory.fromDfType(DfTypes.referenceConstant(psiType, targetClassType))
            }
        }
        return factory.unknown
    }
}