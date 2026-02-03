// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.openapi.util.registry.Registry

internal suspend inline fun executeInMemoryWithFallback(
  crossinline inMemoryOperation: suspend () -> GitCommitEditingOperationResult,
  crossinline fallbackOperation: suspend () -> GitCommitEditingOperationResult
): GitCommitEditingOperationResult {
  if (Registry.`is`("git.in.memory.commit.editing.operations.enabled")) {
    val inMemoryResult = inMemoryOperation()
    if (inMemoryResult is GitCommitEditingOperationResult.Complete) {
      return inMemoryResult
    }
  }

  return fallbackOperation()
}
