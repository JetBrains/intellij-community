// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import gnu.trove.TObjectIntHashMap
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ArgumentsInstruction
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsDfaInstance

class TypesReachingDefinitionsInstance(
  flow: Array<out Instruction>,
  varIndexes: TObjectIntHashMap<VariableDescriptor>
) : ReachingDefinitionsDfaInstance(flow, varIndexes) {

  override fun `fun`(m: DefinitionMap, instruction: Instruction) = when (instruction) {
    is MixinTypeInstruction -> registerDef(m, instruction, instruction.variableDescriptor)
    is ArgumentsInstruction -> {
      for (descriptor in instruction.variableDescriptors) {
        registerDef(m, instruction, descriptor)
      }
    }
    else -> super.`fun`(m, instruction)
  }

  private fun registerDef(m: DefinitionMap, instruction: Instruction, descriptor: VariableDescriptor?) {
    val varIndex = myVarToIndexMap.get(descriptor)
    if (varIndex > 0) {
      m.registerDef(instruction, varIndex)
    }
  }
}
