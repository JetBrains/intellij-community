// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import it.unimi.dsi.fastutil.objects.Object2IntMap
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ArgumentsInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.FunctionalBlockBeginInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.FunctionalBlockEndInstruction
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsDfaInstance

class TypesReachingDefinitionsInstance(
  flow: Array<out Instruction>,
  varIndexes: Object2IntMap<VariableDescriptor>
) : ReachingDefinitionsDfaInstance(flow, varIndexes) {

  override fun `fun`(m: DefinitionMap, instruction: Instruction) : DefinitionMap = when (instruction) {
    is MixinTypeInstruction -> DefinitionMap().apply {
      mergeFrom(m)
      registerDef(m, instruction, instruction.variableDescriptor)
    }
    is ArgumentsInstruction -> {
      val newMap = DefinitionMap()
      newMap.mergeFrom(m)
      for (descriptor in instruction.variableDescriptors) {
        registerDef(newMap, instruction, descriptor)
      }
      newMap
    }
    is FunctionalBlockBeginInstruction -> DefinitionMap().apply { mergeFrom(m); setClosureContext(m) }
    is FunctionalBlockEndInstruction -> DefinitionMap().apply {
      mergeFrom(m)
      mergeFrom(headDefinitionMap)
      popClosureContext()
    }
    else -> super.`fun`(m, instruction)
  }

  private fun registerDef(m: DefinitionMap, instruction: Instruction, descriptor: VariableDescriptor?) {
    val varIndex = myVarToIndexMap.getInt(descriptor)
    if (varIndex > 0) {
      m.registerDef(varIndex, instruction)
    }
  }
}
