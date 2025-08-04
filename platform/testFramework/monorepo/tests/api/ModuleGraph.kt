// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.monorepo.api

import org.jetbrains.jps.model.module.JpsModule

internal fun Iterable<JpsModule>.prepareModuleList(): List<JpsModule> {
  val moduleSet = toSet()

  val graph = Graph(this) { module ->
    module.moduleDependencies { it in moduleSet }
  }

  val cycle = graph.findCycle()
  if (cycle != null) {
    throw AssertionError("Cycle detected: ${cycle.joinToString(separator = " -> ")}")
  }

  return graph.sortedTopologically()
}
