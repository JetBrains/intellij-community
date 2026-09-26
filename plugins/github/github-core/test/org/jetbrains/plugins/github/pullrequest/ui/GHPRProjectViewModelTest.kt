// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.GHRepositoryConnectionManager
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class GHPRProjectViewModelTest {
  // Instance-level (not companion object) fixtures: GHPRProjectViewModel is a project-level service whose lazy
  // properties (connectedProjectVm, selectorVm) pin to whatever StateFlow instances exist on first access, so
  // each test needs its own fresh Project to avoid cross-test state pollution.
  private val projectFixture = projectFixture()
  private val disposableFixture = disposableFixture()

  private val project get() = projectFixture.get()
  private val disposable get() = disposableFixture.get()

  private lateinit var originMapping: GHGitRepositoryMapping
  private lateinit var forkMapping: GHGitRepositoryMapping
  private lateinit var account: GithubAccount
  private lateinit var connectionManager: GHRepositoryConnectionManager
  private lateinit var knownRepositoriesState: MutableStateFlow<Set<GHGitRepositoryMapping>>
  private lateinit var connectionStateFlow: MutableStateFlow<GHRepositoryConnection?>

  @BeforeEach
  fun setUp() {
    val server = GithubServerPath.DEFAULT_SERVER
    originMapping = GHGitRepositoryMapping(GHRepositoryCoordinates(server, GHRepositoryPath("upstream", "repo")), mockk(relaxed = true))
    forkMapping = GHGitRepositoryMapping(GHRepositoryCoordinates(server, GHRepositoryPath("fork-owner", "repo")), mockk(relaxed = true))
    account = GithubAccount(name = "user", server = server)
    knownRepositoriesState = MutableStateFlow(setOf(originMapping, forkMapping))

    val repositoriesManager = mockk<GHHostedRepositoriesManager> {
      every { knownRepositoriesState } returns this@GHPRProjectViewModelTest.knownRepositoriesState
    }
    connectionStateFlow = MutableStateFlow(null)
    connectionManager = mockk<GHRepositoryConnectionManager> {
      every { connectionState } returns connectionStateFlow
      coEvery { openConnection(any(), any()) } answers {
        val repo = firstArg<GHGitRepositoryMapping>()
        val acc = secondArg<GithubAccount>()
        mockk<GHRepositoryConnection>(relaxed = true) {
          every { this@mockk.repo } returns repo
          every { this@mockk.account } returns acc
        }.also { connectionStateFlow.value = it }
      }
    }
    val accountManager = mockk<GHAccountManager>(relaxed = true) {
      every { accountsState } returns MutableStateFlow(setOf(account))
    }
    val connectedProjectVmFactory = mockk<GHPRConnectedProjectViewModelFactory> {
      every { create(any(), any(), any(), any()) } answers {
        val connection = thirdArg<GHRepositoryConnection>()
        mockk<GHPRConnectedProjectViewModel>(relaxed = true) {
          every { repository } returns connection.repo.repository
        }
      }
    }

    project.registerOrReplaceServiceInstance(GHHostedRepositoriesManager::class.java, repositoriesManager, disposable)
    project.registerOrReplaceServiceInstance(GHRepositoryConnectionManager::class.java, connectionManager, disposable)
    project.registerOrReplaceServiceInstance(GHPRConnectedProjectViewModelFactory::class.java, connectedProjectVmFactory, disposable)
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(GHAccountManager::class.java, accountManager, disposable)
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `activateAndAwaitProject connects using the preferred repo and account when multiple repos are known`() = timeoutRunBlocking {
    val vm = project.service<GHPRProjectViewModel>()

    val actionInvoked = CompletableDeferred<Unit>()
    vm.activateAndAwaitProject(forkMapping.repository to account) {
      actionInvoked.complete(Unit)
    }
    // await the full flow, not just openConnection, so nothing is still running after tearDown() unmocks it
    actionInvoked.await()

    coVerify { connectionManager.openConnection(forkMapping, account) }
  }

  @Test
  fun `activateAndAwaitProject does not connect when no preferred repo and account is given`() = timeoutRunBlocking {
    val vm = project.service<GHPRProjectViewModel>()

    vm.activateAndAwaitProject { }

    coVerify(exactly = 0) { connectionManager.openConnection(any(), any()) }
  }

  @Test
  fun `selectorVm does not reconnect once already connected even if the known repo re-emits`() = timeoutRunBlocking {
    // a single known repo/account is what makes selectorVm's auto-connect heuristic fire
    knownRepositoriesState.value = setOf(originMapping)

    val vm = project.service<GHPRProjectViewModel>()
    vm.selectorVm
    connectionStateFlow.first { it != null }

    // re-emit the known repo after the connection is already established
    knownRepositoriesState.value = emptySet()
    knownRepositoriesState.value = setOf(originMapping)
    delay(200) // give a buggy reconnect a chance to happen before asserting it didn't

    coVerify(exactly = 1) { connectionManager.openConnection(originMapping, account) }
  }

  @Test
  fun `activateAndAwaitProject awaits the vm matching the preferred repo, not a stale one from a racing connection`() = timeoutRunBlocking {
    val vm = project.service<GHPRProjectViewModel>()

    // simulate a heuristic connecting first to the wrong repo, with connectedProjectVm already caught up to it
    connectionManager.openConnection(originMapping, account)
    vm.connectedProjectVm.first { it != null }

    val seenRepositories = mutableListOf<GHRepositoryCoordinates>()
    val actionInvoked = CompletableDeferred<Unit>()
    vm.activateAndAwaitProject(forkMapping.repository to account) {
      seenRepositories.add(repository)
      actionInvoked.complete(Unit)
    }
    actionInvoked.await()

    assertEquals(listOf(forkMapping.repository), seenRepositories)
  }

  @Test
  fun `activateAndAwaitProject does not reconnect when a differently-instanced mapping resolves to the same repository and account`() = timeoutRunBlocking {
    val vm = project.service<GHPRProjectViewModel>()

    // Establish the connection with one GHGitRepositoryMapping instance for originMapping's coordinates.
    connectionManager.openConnection(originMapping, account)
    vm.connectedProjectVm.first { it != null }

    // simulate the same repo re-resolving to a structurally different mapping instance (e.g. after git sync)
    val resynchronizedMapping = GHGitRepositoryMapping(originMapping.repository, mockk(relaxed = true))
    knownRepositoriesState.value = setOf(resynchronizedMapping, forkMapping)

    val actionInvoked = CompletableDeferred<Unit>()
    vm.activateAndAwaitProject(originMapping.repository to account) {
      actionInvoked.complete(Unit)
    }
    actionInvoked.await()

    // a redundant openConnection call would tear down the connection and cancel in-flight work
    coVerify(exactly = 1) { connectionManager.openConnection(any(), any()) }
  }
}
