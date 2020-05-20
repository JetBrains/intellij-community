// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.getControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil

fun getLeastUpperBoundByAllWrites(block: GrControlFlowOwner,
                                  descriptor: VariableDescriptor): PsiType {
  val flow: Array<Instruction> = block.controlFlow
  var resultType: PsiType = PsiType.NULL
  for (instruction: Instruction in flow) {
    val inferred: PsiType = if (instruction is ReadWriteVariableInstruction && instruction.descriptor == descriptor) {
      TypeInferenceHelper.getInferredType(instruction.descriptor, instruction, block) ?: PsiType.NULL
    }
    else if (instruction.element is GrFunctionalExpression) {
      val owner: GrControlFlowOwner? = (instruction.element as GrFunctionalExpression).getControlFlowOwner()
      owner?.run { getLeastUpperBoundByAllWrites(owner, descriptor) } ?: PsiType.NULL
    }
    else PsiType.NULL
    resultType = TypesUtil.getLeastUpperBound(resultType, inferred, block.manager) ?: PsiType.NULL
  }
  return resultType
}
