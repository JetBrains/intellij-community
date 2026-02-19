// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration.Companion.minutes

abstract class GitRepositoryDataHolder(val repository: GitRepository, scopeName: String) {

  protected val cs: CoroutineScope = repository.coroutineScope.childScope(scopeName)

  private val updateSemaphore = OverflowSemaphore(overflow = BufferOverflow.DROP_OLDEST)

  fun reload() {
    cs.launch(Dispatchers.IO) {
      updateSemaphore.withPermit {
        updateState()
      }
    }
  }

  protected abstract suspend fun updateState()

  @TestOnly
  internal fun ensureUpToDateForTests() {
    runBlockingMaybeCancellable {
      withContext(Dispatchers.IO) {
        withTimeout(3.minutes) {
          updateSemaphore.withPermit {
            updateState()
          }
        }
      }
    }
  }
}