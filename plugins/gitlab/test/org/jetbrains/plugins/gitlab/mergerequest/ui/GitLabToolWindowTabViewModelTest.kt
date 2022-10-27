// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowTabViewModel.NestedViewModel
import org.jetbrains.plugins.gitlab.testutil.MainDispatcherRule
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.junit.*
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.*
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

  @Mock
  internal lateinit var connectionManager: GitLabProjectConnectionManager

  @Test
  fun `connection initialized`() = runTest {
    whenever(accountManager.accountsState) doReturn MutableStateFlow(emptySet())
    whenever(accountManager.getCredentialsState(any(), any())) doReturn MutableStateFlow("")
    whenever(projectManager.knownRepositoriesState) doReturn MutableStateFlow(emptySet())

    val scope = childScope(Dispatchers.Main)
    val vm = GitLabToolWindowTabViewModel(scope, connectionManager, projectManager, accountManager)

    val nestedVm = vm.nestedViewModelState.value
    assert(nestedVm is NestedViewModel.Selectors)

    val repo = GitLabProjectMapping(mock(), mock())
    val acc = mock<GitLabAccount>()
    (nestedVm as NestedViewModel.Selectors).selectorVm.apply {
      repoSelectionState.value = repo
      accountSelectionState.value = acc
      submitSelection()
    }
    verify(connectionManager, times(1)).connect(scope, repo, acc)

    scope.cancel()
  }

  companion object {
    @JvmField
    @ClassRule
    val mainRule = MainDispatcherRule()
  }
}

