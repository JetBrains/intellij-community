// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.readWrite

import it.unimi.dsi.fastutil.objects.Object2IntMap
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance

internal class ReadBeforeWriteInstance(
  private val varIndex: Object2IntMap<VariableDescriptor>,
  private val onlyFirst: Boolean
) : DfaInstance<ReadBeforeWriteState> {

  override fun `fun`(state: ReadBeforeWriteState, instruction: Instruction) {
    if (instruction !is ReadWriteVariableInstruction) return
    val nameId = varIndex.getInt(instruction.descriptor)
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
