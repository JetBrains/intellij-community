// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerOrReplaceServiceInstance
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class GitLabProjectViewModelTest {
  // GitLabProjectViewModel is a project-level service, so each test needs its own fresh project.
  private val projectFixture = projectFixture()
  private val disposableFixture = disposableFixture()

  private val project get() = projectFixture.get()
  private val disposable get() = disposableFixture.get()

  private lateinit var originMapping: GitLabProjectMapping
  private lateinit var forkMapping: GitLabProjectMapping
  private lateinit var account: GitLabAccount
  private lateinit var connectionManager: GitLabProjectConnectionManager
  private lateinit var knownRepositoriesState: MutableStateFlow<Set<GitLabProjectMapping>>

  @BeforeEach
  fun setUp() {
    val server = GitLabServerPath.DEFAULT_SERVER
    originMapping = GitLabProjectMapping(GitLabProjectCoordinates(server, GitLabProjectPath("upstream", "repo")), mockk(relaxed = true))
    forkMapping = GitLabProjectMapping(GitLabProjectCoordinates(server, GitLabProjectPath("fork-owner", "repo")), mockk(relaxed = true))
    account = GitLabAccount(name = "user", server = server)
    knownRepositoriesState = MutableStateFlow(setOf(originMapping, forkMapping))

    val projectsManager = mockk<GitLabProjectsManager> {
      every { knownRepositoriesState } returns this@GitLabProjectViewModelTest.knownRepositoriesState
    }
    val connectionStateFlow = MutableStateFlow<GitLabProjectConnection?>(null)
    connectionManager = mockk<GitLabProjectConnectionManager> {
      every { connectionState } returns connectionStateFlow
      coEvery { openConnection(any(), any()) } answers {
        val repo = firstArg<GitLabProjectMapping>()
        val acc = secondArg<GitLabAccount>()
        val connection = mockk<GitLabProjectConnection>(relaxed = true)
        every { connection.repo } returns repo
        every { connection.account } returns acc
        connection.also { connectionStateFlow.value = it }
      }
    }
    val accountManager = mockk<GitLabAccountManager>(relaxed = true) {
      every { accountsState } returns MutableStateFlow(setOf(account))
    }
    val connectedProjectVmFactory = mockk<GitLabConnectedProjectViewModelFactory> {
      every { create(any(), any(), any(), any(), any(), any()) } answers {
        val connection = arg<GitLabProjectConnection>(4)
        mockk<GitLabConnectedProjectViewModel>(relaxed = true) {
          every { projectCoordinates } returns connection.repo.repository
        }
      }
    }

    project.registerOrReplaceServiceInstance(GitLabProjectsManager::class.java, projectsManager, disposable)
    project.registerOrReplaceServiceInstance(GitLabProjectConnectionManager::class.java, connectionManager, disposable)
    project.registerOrReplaceServiceInstance(GitLabConnectedProjectViewModelFactory::class.java, connectedProjectVmFactory, disposable)
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(GitLabAccountManager::class.java, accountManager, disposable)
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `activateAndAwaitProject connects using the preferred project and account when multiple projects are known`() = timeoutRunBlocking {
    val vm = project.service<GitLabProjectViewModel>()

    val actionInvoked = CompletableDeferred<Unit>()
    vm.activateAndAwaitProject(originMapping.repository to account) {
      actionInvoked.complete(Unit)
    }
    // Await the full flow, so nothing still runs after tearDown() unmocks the services.
    actionInvoked.await()

    coVerify { connectionManager.openConnection(originMapping, account) }
  }

  @Test
  fun `activateAndAwaitProject does not connect when no preferred project and account is given`() = timeoutRunBlocking {
    val vm = project.service<GitLabProjectViewModel>()

    vm.activateAndAwaitProject { }

    coVerify(exactly = 0) { connectionManager.openConnection(any(), any()) }
  }

  @Test
  fun `activateAndAwaitProject awaits the vm matching the preferred project, not a stale one from a racing connection`() =
    timeoutRunBlocking {
      val vm = project.service<GitLabProjectViewModel>()

      // Simulate a connection to the wrong project first, with connectedProjectVm already caught up to it.
      connectionManager.openConnection(originMapping, account)
      vm.connectedProjectVm.first { it != null }

      val seenProjects = mutableListOf<GitLabProjectCoordinates>()
      val actionInvoked = CompletableDeferred<Unit>()
      vm.activateAndAwaitProject(forkMapping.repository to account) {
        seenProjects.add(projectCoordinates)
        actionInvoked.complete(Unit)
      }
      actionInvoked.await()

      assertEquals(listOf(forkMapping.repository), seenProjects)
    }

  @Test
  fun `activateAndAwaitProject does not reconnect when a different mapping resolves to the same project and account`() =
    timeoutRunBlocking {
      val vm = project.service<GitLabProjectViewModel>()

      connectionManager.openConnection(originMapping, account)
      vm.connectedProjectVm.first { it != null }

      // Simulate the same project re-resolving to a different mapping instance, for example after a git sync.
      val resynchronizedMapping = GitLabProjectMapping(originMapping.repository, mockk(relaxed = true))
      knownRepositoriesState.value = setOf(resynchronizedMapping, forkMapping)

      val actionInvoked = CompletableDeferred<Unit>()
      vm.activateAndAwaitProject(originMapping.repository to account) {
        actionInvoked.complete(Unit)
      }
      actionInvoked.await()

      // A redundant openConnection call closes the connection and cancels in-flight work.
      coVerify(exactly = 1) { connectionManager.openConnection(any(), any()) }
    }
}
