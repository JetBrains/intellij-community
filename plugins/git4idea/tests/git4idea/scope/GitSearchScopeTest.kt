// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.scope

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.search.SearchScopeProvider
import com.intellij.testFramework.common.waitUntil
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import git4idea.search.GitIgnoreSearchScope
import git4idea.search.GitSearchScopeProvider
import git4idea.search.GitTrackedSearchScope
import git4idea.test.GitSingleRepoTest
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

class GitSearchScopeTest : GitSingleRepoTest() {
  fun `test no scope is provided if no git repo registered`() {
    val scopeProvider = SearchScopeProvider.EP_NAME.extensionList.filterIsInstance<GitSearchScopeProvider>().single()
    awaitEvents()
    val gitSearchScopes = scopeProvider.getGeneralSearchScopes(project, DataContext.EMPTY_CONTEXT)
    assertNotNull(gitSearchScopes.find { it is GitIgnoreSearchScope })
    assertNotNull(gitSearchScopes.find { it is GitTrackedSearchScope })
    assertNotEmpty(scopeProvider.getGeneralSearchScopes(project, DataContext.EMPTY_CONTEXT))
    vcsManager.unregisterVcs(vcs)
    VcsRepositoryManager.getInstance(myProject).waitForAsyncTaskCompletion()
    assertEmpty(scopeProvider.getGeneralSearchScopes(project, DataContext.EMPTY_CONTEXT))
  }
}

internal fun GitSingleRepoTest.awaitEvents() {
  AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()
  runBlocking {
    repo.untrackedFilesHolder.apply {
      invalidate()
      awaitNotBusy()
    }

    waitUntil("Untracked and ignored holders initialized", timeout = 5.seconds, condition = {
      repo.untrackedFilesHolder.isInitialized && repo.ignoredFilesHolder.initialized
    })
  }
}
