// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.testFramework.assertInstanceOf
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.TestGitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.testutil.MainDispatcherRule
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.junit.*
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabToolWindowTabViewModelTest {

  @Rule
  @JvmField
  val mockitoRule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

  @Mock
  internal lateinit var accountManager: GitLabAccountManager

  @Mock
  internal lateinit var projectManager: GitLabProjectsManager

  private val connectionManager = TestGitLabProjectConnectionManager()

  @Test
  fun `check nested vm`() = runTest {
    whenever(accountManager.accountsState) doReturn MutableStateFlow(emptySet())
    whenever(projectManager.knownRepositoriesState) doReturn MutableStateFlow(emptySet())

    val scope = childScope(Dispatchers.Main)
    val vm = GitLabToolWindowTabViewModel(scope, connectionManager, projectManager, accountManager)

    assertInstanceOf<GitLabToolWindowTabViewModel.NestedViewModel.Selectors>(vm.nestedViewModelState.value)
    connectionManager.tryConnect(GitLabProjectMapping(mock(), mock()), mock())
    assertInstanceOf<GitLabToolWindowTabViewModel.NestedViewModel.MergeRequests>(vm.nestedViewModelState.value)
    connectionManager.disconnect()
    assertInstanceOf<GitLabToolWindowTabViewModel.NestedViewModel.Selectors>(vm.nestedViewModelState.value)
    scope.cancel()
  }

  companion object {
    @JvmField
    @ClassRule
    val mainRule = MainDispatcherRule()
  }
}

