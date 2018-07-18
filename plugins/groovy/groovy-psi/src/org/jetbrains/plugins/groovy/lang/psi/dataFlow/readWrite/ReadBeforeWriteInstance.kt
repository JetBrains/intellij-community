/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.readWrite

import gnu.trove.TObjectIntHashMap
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance

class ReadBeforeWriteInstance(val nameIndex: TObjectIntHashMap<String>, val onlyFirst: Boolean) : DfaInstance<ReadBeforeWriteState> {

  override fun `fun`(state: ReadBeforeWriteState, instruction: Instruction) {
    if (instruction !is ReadWriteVariableInstruction) return
    val nameId = nameIndex.get(instruction.variableName)
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

  override fun initial(): ReadBeforeWriteState = ReadBeforeWriteState.bottom
}