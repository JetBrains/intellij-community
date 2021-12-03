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
    is MixinTypeInstruction -> m.withRegisteredDef(myVarToIndexMap.getInt(instruction.variableDescriptor), instruction)
    is ArgumentsInstruction -> {
      var newMap = m
      for (descriptor in instruction.variableDescriptors) {
        newMap = newMap.withRegisteredDef(myVarToIndexMap.getInt(descriptor), instruction)
      }
      newMap
    }
    is FunctionalBlockBeginInstruction -> m.withNewClosureContext(m)
    is FunctionalBlockEndInstruction -> m.withMerged(m.topClosureState).withoutClosureContext()
    else -> super.`fun`(m, instruction)
  }
}
