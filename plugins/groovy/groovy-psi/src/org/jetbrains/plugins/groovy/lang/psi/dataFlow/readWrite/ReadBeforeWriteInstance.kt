// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.readWrite

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance
import java.util.*

internal class ReadBeforeWriteInstance(
  private val onlyFirst: Boolean
) : DfaInstance<ReadBeforeWriteState> {

  override fun `fun`(state: ReadBeforeWriteState, instruction: Instruction) : ReadBeforeWriteState {
    if (instruction !is ReadWriteVariableInstruction) return state
    val nameId = instruction.descriptor
    if (nameId < 0) return state

    if (instruction.isWrite) {
      val newState = ReadBeforeWriteState(state.writes.clone() as BitSet, state.reads)
      newState.writes.set(nameId)
      return newState
    }
    else {
      if (!state.writes.get(nameId)) {
        val reads = (state.reads.clone() as BitSet).also { it.set(instruction.num()) }
        val writes = if (onlyFirst) (state.writes.clone() as BitSet).also { it.set(nameId) } else state.writes
        return ReadBeforeWriteState(writes, reads)
      } else {
        return state
      }
    }
  }

  override fun isReachable(): Boolean = true
}
