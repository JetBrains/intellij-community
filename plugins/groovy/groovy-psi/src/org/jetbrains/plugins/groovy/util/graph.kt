// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.OutboundSemiGraph

internal fun <T> mapGraph(map: Map<T, Collection<T>>): OutboundSemiGraph<T> = object : OutboundSemiGraph<T> {
  override fun getNodes(): Collection<T> = map.keys
  override fun getOut(n: T): Iterator<T> = map.getOrDefault(n, emptyList()).iterator()
}

/**
 * @return set of nodes outside of any cycles
 */
internal fun <T> findNodesOutsideCycles(graph: OutboundSemiGraph<T>): Set<T> {
  val builder = DFSTBuilder(graph)
  val components = builder.components
  val result = HashSet<T>()
  for (component in components) {
    result.add(component.singleOrNull() ?: continue)
  }
  return result
}
