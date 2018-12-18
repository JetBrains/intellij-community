// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.OutboundSemiGraph
import gnu.trove.TIntHashSet
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import java.util.*

/**
 * @return set of instructions outside of any cycles
 */
fun computeAcyclicInstructions(flow: Array<out Instruction>): TIntHashSet {
  val instructions = Arrays.asList(*flow)
  val graph = object : OutboundSemiGraph<Instruction> {
    override fun getNodes(): Collection<Instruction> = instructions
    override fun getOut(n: Instruction): Iterator<Instruction> = n.allSuccessors().iterator()
  }
  val builder = DFSTBuilder(graph)
  val components = builder.components
  val result = TIntHashSet()
  for (component in components) {
    val instruction = component.singleOrNull()
    if (instruction != null) {
      result.add(instruction.num())
    }
  }
  return result
}
