// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.monorepo.api

import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.OutboundSemiGraph
import org.jetbrains.jps.model.module.JpsModule

internal fun List<JpsModule>.prepareModuleList(): List<JpsModule> {
  val graph = DepGraph(this)

  val builder = DFSTBuilder(graph)
  builder.circularDependency?.let { circularDependency ->
    throw AssertionError("Cycle detected: ${circularDependency}")
  }

  return builder.sortedNodes.reversed()
}

private class DepGraph(
  val modules: List<JpsModule>,
) : OutboundSemiGraph<JpsModule> {
  private val moduleSet = modules.toSet()

  override fun getNodes(): Collection<JpsModule> =
    modules

  override fun getOut(node: JpsModule): Iterator<JpsModule?> =
    node.moduleDependencies { it in moduleSet }.iterator()
}