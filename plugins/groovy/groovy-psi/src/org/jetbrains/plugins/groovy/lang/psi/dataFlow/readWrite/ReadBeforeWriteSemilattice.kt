// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.readWrite

import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice

object ReadBeforeWriteSemilattice : Semilattice<ReadBeforeWriteState> {

  private val bottom: ReadBeforeWriteState = ReadBeforeWriteState()

  override fun initial(): ReadBeforeWriteState = bottom

  override fun join(ins: MutableList<out ReadBeforeWriteState>): ReadBeforeWriteState {
    val states = ins.filter { it !== bottom }
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
    if (e1 === bottom || e2 === bottom) return e1 === e2
    return e1.writes == e2.writes && e1.reads == e2.reads
  }
}
