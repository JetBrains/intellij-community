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

import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice

object ReadBeforeWriteSemilattice : Semilattice<ReadBeforeWriteState> {

  override fun join(ins: List<ReadBeforeWriteState>): ReadBeforeWriteState {
    val states = ins.filter { it !== ReadBeforeWriteState.bottom }
    if (states.isEmpty()) return ReadBeforeWriteState()
    val iterator = states.iterator()
    val accumulator = iterator.next().clone() // reduce optimized
    while (iterator.hasNext()) {
      val it = iterator.next()
      accumulator.writes.and(it.writes)
      accumulator.reads.or(it.reads)
    }
    return accumulator
  }

  override fun eq(e1: ReadBeforeWriteState, e2: ReadBeforeWriteState): Boolean {
    if (e1 === e2) return true
    if (e1 === ReadBeforeWriteState.bottom || e2 === ReadBeforeWriteState.bottom) return e1 === e2
    return e1.writes == e2.writes && e1.reads == e2.reads
  }
}