// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.util.childScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.testutil.MainDispatcherRule
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabRepositoryAndAccountSelectorViewModelTest {

  companion object {
    @JvmField
    @ClassRule
    val mainRule = MainDispatcherRule()
  }

  @Rule
  @JvmField
  val mockitoRule: MockitoRule = MockitoJUnit.rule()

  @Mock
  internal lateinit var projectManager: GitLabProjectsManager

  @Mock
  internal lateinit var accountManager: GitLabAccountManager

  @Test
  fun `initial selection`() = runTest {
    val projectMapping = mock<GitLabProjectMapping> {
      on { repository } doAnswer {
        mock {
          on { serverPath } doReturn GitLabServerPath.DEFAULT_SERVER
        }
      }
    }

    whenever(projectManager.knownRepositoriesState) doReturn MutableStateFlow(setOf(projectMapping))

    val account = GitLabAccount(name = "test", server = GitLabServerPath.DEFAULT_SERVER)
    whenever(accountManager.accountsState) doReturn MutableStateFlow(setOf(account))
    whenever(accountManager.getCredentialsState(any(), any())) doReturn MutableStateFlow("")

    val scope = childScope(Dispatchers.Main)
    val vm = GitLabRepositoryAndAccountSelectorViewModel(scope, projectManager, accountManager) { _, _ -> mock() }

    assertEquals(null, vm.repoSelectionState.value)
    assertEquals(null, vm.accountSelectionState.value)
    assertEquals(false, vm.missingCredentialsState.value)
    assertEquals(false, vm.tokenLoginAvailableState.value)
    assertEquals(false, vm.submitAvailableState.value)

    vm.repoSelectionState.value = projectMapping

    assertEquals(projectMapping, vm.repoSelectionState.value)
    assertEquals(null, vm.accountSelectionState.value)
    assertEquals(false, vm.missingCredentialsState.value)
    assertEquals(true, vm.tokenLoginAvailableState.value)
    assertEquals(false, vm.submitAvailableState.value)

    vm.accountSelectionState.value = account

    assertEquals(projectMapping, vm.repoSelectionState.value)
    assertEquals(account, vm.accountSelectionState.value)
    assertEquals(false, vm.missingCredentialsState.value)
    assertEquals(false, vm.tokenLoginAvailableState.value)
    assertEquals(true, vm.submitAvailableState.value)

    scope.cancel()
  }
}