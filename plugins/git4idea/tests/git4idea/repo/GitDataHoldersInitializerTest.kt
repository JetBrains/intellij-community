// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.changes.ChangesViewModifier
import com.intellij.openapi.vcs.changes.ChangesViewModifier.ChangesViewModifierListener
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.vcs.git.refreshChangesViewOnceRepositoriesAreReady
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class GitDataHoldersInitializerTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `test Changes view is asked to refresh once repositories holder is ready`(): Unit = with(context) {
    val updateCount = AtomicInteger(0)
    project.messageBus.connect(disposable)
      .subscribe(ChangesViewModifier.TOPIC, ChangesViewModifierListener { updateCount.incrementAndGet() })

    assertThat(GitRepositoriesHolder.getInstance(project).initialized).isFalse()

    runBlocking {
      refreshChangesViewOnceRepositoriesAreReady(project)
    }

    assertThat(GitRepositoriesHolder.getInstance(project).initialized).isTrue()
    assertThat(updateCount.get()).isEqualTo(1)
  }
}
