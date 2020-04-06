// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.psi.PsiElement
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
                                              mainFlow: Array<Instruction>,
                                              usages: List<GrControlFlowOwner>): DFAType {
  val previousInstructions: Set<Instruction?> = ControlFlowUtils.getPrecedingFlow(instruction)
  var currentType = initialType
  loop@ for (block in usages) {
    val topBlock: PsiElement = block.parentsWithSelf.find {
      it is GrControlFlowOwner && ControlFlowUtils.findInstruction(it, mainFlow) != null
    } ?: continue
    val flowInstruction: Instruction? = ControlFlowUtils.findInstruction(topBlock, mainFlow) ?: continue
    if (!previousInstructions.contains(flowInstruction)) continue
    val functionalExpression: GrFunctionalExpression = when (block) {
      is GrLambdaBody -> block.lambdaExpression
      is GrClosableBlock -> block
      else -> continue@loop
    }
    val kind = functionalExpression.getInvocationKind()
    if (kind === InvocationKind.UNKNOWN) {
      currentType = DFAType.create(currentType, DFAType.create(getLeastUpperBoundByAllWrites(block, descriptor)), block.manager)
    }
  }
  return currentType
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
