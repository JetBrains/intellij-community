// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.psi.PsiType
import com.intellij.psi.util.parentsWithSelf
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaBody
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InvocationKind
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.getInvocationKind
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil

internal fun weakenTypeIfUsedInUnknownClosure(descriptor: VariableDescriptor,
                                              initialType: DFAType,
                                              instruction: Instruction,
                                              usages: List<GrControlFlowOwner>): DFAType {
  var currentType: DFAType = initialType
  val interestingUsages: List<GrControlFlowOwner> = usages.filter { it.isPrecedeInstruction(instruction) }
  for (block: GrControlFlowOwner in interestingUsages) {
    val kind = block.getFunctionalExpression()?.getInvocationKind()
    if (kind === InvocationKind.UNKNOWN) {
      currentType = DFAType.create(currentType, DFAType.create(getLeastUpperBoundByAllWrites(block, descriptor)), block.manager)
    }
  }
  return currentType
}

private fun GrControlFlowOwner.getFunctionalExpression(): GrFunctionalExpression? = when (this) {
  is GrLambdaBody -> lambdaExpression
  is GrClosableBlock -> this
  else -> null
}

private fun GrControlFlowOwner.isPrecedeInstruction(instruction: Instruction): Boolean {
  val previousInstructions: Array<Instruction> = ControlFlowUtils.getPrecedingFlow(instruction).toTypedArray()
  return this.parentsWithSelf.any {
    it is GrControlFlowOwner && ControlFlowUtils.findInstruction(it, previousInstructions) != null
  }
}

private fun getLeastUpperBoundByAllWrites(block: GrControlFlowOwner,
                                          descriptor: VariableDescriptor): PsiType {
  val flow: Array<Instruction> = block.controlFlow
  var resultType: PsiType = PsiType.NULL
  for (instruction in flow) {
    if (instruction is ReadWriteVariableInstruction && instruction.descriptor.getName() === descriptor.getName()) {
      val inferred: PsiType = TypeInferenceHelper.getInferredType(instruction.descriptor, instruction, block) ?: PsiType.NULL
      resultType = TypesUtil.getLeastUpperBound(resultType, inferred, block.manager) ?: PsiType.NULL
    }
  }
  return resultType
}
