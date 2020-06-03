// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.OutboundSemiGraph
import gnu.trove.THashSet
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction

fun <T> mapGraph(map: Map<T, Collection<T>>): OutboundSemiGraph<T> = object : OutboundSemiGraph<T> {
  override fun getNodes(): Collection<T> = map.keys
  override fun getOut(n: T): Iterator<T> = map.getOrDefault(n, emptyList()).iterator()
}

fun mapFlow(flow: Array<Instruction>) = object : OutboundSemiGraph<Instruction> {
  override fun getNodes(): Collection<Instruction> = flow.toList()
  override fun getOut(instruction: Instruction): Iterator<Instruction> = instruction.allSuccessors().iterator()
}

/**
 * @return set of nodes outside of any cycles
 */
fun <T> findNodesOutsideCycles(graph: OutboundSemiGraph<T>): Set<T> {
  val builder = DFSTBuilder(graph)
  val components = builder.components
  val result = THashSet<T>()
  for (component in components) {
    val node = component.singleOrNull() ?: continue
    result.add(node)
  }
  return result
}
