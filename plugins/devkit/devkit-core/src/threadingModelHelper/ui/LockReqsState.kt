// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper.ui

import androidx.compose.runtime.Immutable
import org.jetbrains.idea.devkit.threadingModelHelper.ExecutionPath
import org.jetbrains.idea.devkit.threadingModelHelper.LockType

@Immutable
internal data class LockReqsViewState(
  val allPaths: List<ExecutionPath> = emptyList(),
  val query: String = "",
  val selectedTypes: Set<LockType> = LockType.entries.toSet(),
  val selected: ExecutionPath? = null,
) {
  val filteredPaths: List<ExecutionPath>
    get() {
      if (allPaths.isEmpty()) return emptyList()
      val q = query.trim()
      return allPaths.asSequence()
        .filter { it.lockRequirement.lockType in selectedTypes }
        .filter { path ->
          if (q.isEmpty()) return@filter true
          val chain = path.methodChain.joinToString(" -> ") { c ->
            "${c.method.containingClass?.qualifiedName}.${c.method.name}"
          }
          chain.contains(q, ignoreCase = true)
        }
        .toList()
    }
}
