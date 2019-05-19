// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.readWrite

import gnu.trove.TObjectIntHashMap
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance

class ReadBeforeWriteInstance(
  private val varIndex: TObjectIntHashMap<VariableDescriptor>,
  private val onlyFirst: Boolean
) : DfaInstance<ReadBeforeWriteState> {

  override fun `fun`(state: ReadBeforeWriteState, instruction: Instruction) {
    if (instruction !is ReadWriteVariableInstruction) return
    val nameId = varIndex.get(instruction.descriptor)
    if (nameId < 0) return

    if (instruction.isWrite) {
      state.writes.set(nameId)
    }
    else {
      if (!state.writes.get(nameId)) {
        state.reads.set(instruction.num())
        if (onlyFirst) {
          state.writes.set(nameId)
        }
      }
    }
  }

  override fun isReachable(): Boolean = true
}
