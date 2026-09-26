// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.scope

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.search.SearchScopeProvider
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import git4idea.repo.GitRepositoryManager
import git4idea.search.GitIgnoreSearchScope
import git4idea.search.GitSearchScopeProvider
import git4idea.search.GitTrackedSearchScope
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
class GitSearchScopeTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @Test
  fun `test no scope is provided if no git repo registered`(): Unit = with(context) {
    val scopeProvider = SearchScopeProvider.EP_NAME.extensionList.filterIsInstance<GitSearchScopeProvider>().single()
    awaitEvents()
    val gitSearchScopes = scopeProvider.getGeneralSearchScopes(project, DataContext.EMPTY_CONTEXT)
    assertThat(gitSearchScopes).anyMatch { it is GitIgnoreSearchScope }
    assertThat(gitSearchScopes).anyMatch { it is GitTrackedSearchScope }
    assertThat(scopeProvider.getGeneralSearchScopes(project, DataContext.EMPTY_CONTEXT)).isNotEmpty()
    vcsManager.unregisterVcs(vcs)
    VcsRepositoryManager.getInstance(project).waitForAsyncTaskCompletion()
    assertThat(scopeProvider.getGeneralSearchScopes(project, DataContext.EMPTY_CONTEXT)).isEmpty()
  }
}

internal fun GitSingleRepoContext.awaitEvents() {
  AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()
  runBlocking {
    val repositories = GitRepositoryManager.getInstance(project).repositories
    repositories.forEach { it.untrackedFilesHolder.invalidate() }
    repositories.forEach { it.untrackedFilesHolder.awaitNotBusy() }

    waitUntil("Untracked and ignored holders initialized", timeout = 5.seconds, condition = {
      repositories.all { it.untrackedFilesHolder.isInitialized && it.ignoredFilesHolder.initialized }
    })
  }
}
