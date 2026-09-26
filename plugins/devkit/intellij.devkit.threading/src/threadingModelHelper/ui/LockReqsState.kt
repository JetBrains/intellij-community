// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper.ui

import androidx.compose.runtime.Immutable
import org.jetbrains.idea.devkit.threadingModelHelper.ConstraintType
import org.jetbrains.idea.devkit.threadingModelHelper.ExecutionPath

@Immutable
internal data class LockReqsViewState(
  val allPaths: List<ExecutionPath> = emptyList(),
  val query: String = "",
  val selectedTypes: Set<ConstraintType> = ConstraintType.entries.toSet(),
  val selected: ExecutionPath? = null,
) {
  val filteredPaths: List<ExecutionPath>
    get() {
      if (allPaths.isEmpty()) return emptyList()
      val q = query.trim()
      return allPaths.asSequence()
        .filter { it.lockRequirement.constraintType in selectedTypes }
        .filter { path ->
          if (q.isEmpty()) return@filter true
          val chain = path.methodChain.joinToString(" -> ") { c ->
            "${c.containingClassName}.${c.methodName}"
          }
          chain.contains(q, ignoreCase = true)
        }
        .toList()
    }
}
