// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.vcs.changes.ChangesViewModifier
import com.intellij.openapi.vcs.changes.ChangesViewModifier.ChangesViewModifierListener
import com.intellij.vcs.git.refreshChangesViewOnceRepositoriesAreReady
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.test.GitSingleRepoTest
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

class GitDataHoldersInitializerTest : GitSingleRepoTest() {
  fun `test Changes view is asked to refresh once repositories holder is ready`() {
    val updateCount = AtomicInteger(0)
    project.messageBus.connect(testRootDisposable)
      .subscribe(ChangesViewModifier.TOPIC, ChangesViewModifierListener { updateCount.incrementAndGet() })

    assertFalse(GitRepositoriesHolder.getInstance(project).initialized)

    runBlocking {
      refreshChangesViewOnceRepositoriesAreReady(project)
    }

    assertTrue(GitRepositoriesHolder.getInstance(project).initialized)
    assertEquals(1, updateCount.get())
  }
}
