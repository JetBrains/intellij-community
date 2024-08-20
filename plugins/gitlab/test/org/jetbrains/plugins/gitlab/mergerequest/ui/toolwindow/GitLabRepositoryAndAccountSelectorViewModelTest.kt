// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.util.MainDispatcherRule
import com.intellij.platform.util.coroutines.childScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabRepositoryAndAccountSelectorViewModel
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Test

internal class GitLabRepositoryAndAccountSelectorViewModelTest {

  companion object {
    @JvmField
    @ClassRule
    val mainRule = MainDispatcherRule()
  }

  private val projectManager = mockk<GitLabProjectsManager>()
  private val accountManager = mockk<GitLabAccountManager>()

  @Test
  fun `single mapping and account initial selection`() = runTest {
    val projectMapping = mockk<GitLabProjectMapping> {
      every { repository } answers {
        mockk {
          every { serverPath } returns GitLabServerPath.DEFAULT_SERVER
        }
      }
    }

    every { projectManager.knownRepositoriesState } returns MutableStateFlow(setOf(projectMapping))

    val account = GitLabAccount(name = "test", server = GitLabServerPath.DEFAULT_SERVER)
    every { accountManager.accountsState } returns MutableStateFlow(setOf(account))
    coEvery { accountManager.getCredentialsState(any(), any()) } returns MutableStateFlow("")
    every { accountManager.canPersistCredentials } returns MutableStateFlow(true)

    val scope = childScope(Dispatchers.Main)
    val vm = GitLabRepositoryAndAccountSelectorViewModel(scope, projectManager, accountManager) { _, _ -> mockk() }

    assertEquals(projectMapping, vm.repoSelectionState.value)
    assertEquals(account, vm.accountSelectionState.value)
    assertEquals(false, vm.missingCredentialsState.value)
    assertEquals(false, vm.tokenLoginAvailableState.value)
    assertEquals(true, vm.submitAvailableState.value)

    scope.cancel()
  }

  @Test
  fun `multiple accounts initial selection`() = runTest {
    val projectMapping = mockk<GitLabProjectMapping> {
      every { repository } answers {
        mockk {
          every { serverPath } returns GitLabServerPath.DEFAULT_SERVER
        }
      }
    }

    every { projectManager.knownRepositoriesState } returns MutableStateFlow(setOf(projectMapping))

    val account = GitLabAccount(name = "test", server = GitLabServerPath.DEFAULT_SERVER)
    val secondAccount = GitLabAccount(name = "secondAccount", server = GitLabServerPath.DEFAULT_SERVER)
    every { accountManager.accountsState } returns MutableStateFlow(setOf(account, secondAccount))
    coEvery { accountManager.getCredentialsState(any(), any()) } returns MutableStateFlow("")
    every { accountManager.canPersistCredentials } returns MutableStateFlow(true)

    val scope = childScope(Dispatchers.Main)
    val vm = GitLabRepositoryAndAccountSelectorViewModel(scope, projectManager, accountManager) { _, _ -> mockk() }

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