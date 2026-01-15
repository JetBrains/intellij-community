// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.project.Project
import com.intellij.testFramework.UsefulTestCase
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import fleet.util.logging.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlin.time.Duration.Companion.seconds

internal fun GitRepositoriesHolder.Companion.getAndInit(project: Project): GitRepositoriesHolder = getInstance(project).also {
  runBlocking {
    it.awaitInitialization()
  }
}

internal fun GitRepositoriesHolder.expectEvent(
  operation: suspend () -> Unit,
  condition: (currentEvent: GitRepositoriesHolder.UpdateType, previousEvents: List<GitRepositoriesHolder.UpdateType>) -> Boolean,
) {
  runBlocking {
    @Suppress("RAW_SCOPE_CREATION")
    val scope = CoroutineScope(SupervisorJob())
    try {
      expectEvent(scope, updates, operation, condition)
    }
    finally {
      scope.coroutineContext.job.cancelAndJoin()
    }
  }
}

private suspend fun expectEvent(
  scope: CoroutineScope,
  updatesFlow: SharedFlow<GitRepositoriesHolder.UpdateType>,
  operation: suspend () -> Unit,
  condition: (GitRepositoriesHolder.UpdateType, List<GitRepositoriesHolder.UpdateType>) -> Boolean,
): GitRepositoriesHolder.UpdateType {
  val updates = updatesFlow.shareIn(scope, SharingStarted.Eagerly, replay = 100)
  operation()
  val collected = mutableListOf<GitRepositoriesHolder.UpdateType>()
  return withTimeout(5.seconds) {
    updates.first {
      logger<UsefulTestCase>().info("Received update: $it")
      collected.add(it)
      condition(it, collected)
    }
  }
}