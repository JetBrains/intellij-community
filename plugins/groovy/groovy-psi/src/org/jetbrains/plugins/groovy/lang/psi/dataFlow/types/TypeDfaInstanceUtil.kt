// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ArgumentsInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.getControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil

fun getLeastUpperBoundByAllWrites(block: GrControlFlowOwner,
                                  initialTypes: Map<VariableDescriptor, DFAType>,
                                  descriptor: VariableDescriptor): PsiType {
  val flow: Array<Instruction> = block.controlFlow
  val types = mutableSetOf<PsiType>()
  val cache = TypeInferenceHelper.getInferenceCache(block)
  for (instruction: Instruction in flow) {
    if (instruction is ReadWriteVariableInstruction && instruction.isWrite && instruction.descriptor == descriptor) {
      val inferredType = cache.getInferredType(instruction.descriptor, instruction, false, initialTypes) ?: continue
      types.add(inferredType)
    }
    else if (instruction !is ReadWriteVariableInstruction &&
             instruction !is ArgumentsInstruction &&
             instruction.element is GrFunctionalExpression) {
      val nestedFlowOwner: GrControlFlowOwner? = (instruction.element as GrFunctionalExpression).getControlFlowOwner()
      if (nestedFlowOwner != null && ControlFlowUtils.getOverwrittenForeignVariableDescriptors(nestedFlowOwner).contains(descriptor)) {
        types.add(getLeastUpperBoundByAllWrites(nestedFlowOwner, initialTypes, descriptor))
      }
    }
  }
  return if (types.isEmpty()) {
    PsiType.NULL
  }
  else {
    TypesUtil.getLeastUpperBound(types.toTypedArray(), block.manager)
  }
}
