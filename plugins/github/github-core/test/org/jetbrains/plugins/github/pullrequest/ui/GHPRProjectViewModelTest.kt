// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

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
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.GHRepositoryConnectionManager
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class GHPRProjectViewModelTest {
  private companion object {
    val projectFixture = projectFixture()
    val disposableFixture = disposableFixture()
  }

  private val project get() = projectFixture.get()
  private val disposable get() = disposableFixture.get()

  private lateinit var originMapping: GHGitRepositoryMapping
  private lateinit var forkMapping: GHGitRepositoryMapping
  private lateinit var account: GithubAccount
  private lateinit var connectionManager: GHRepositoryConnectionManager

  @BeforeEach
  fun setUp() {
    val server = GithubServerPath.DEFAULT_SERVER
    originMapping = GHGitRepositoryMapping(GHRepositoryCoordinates(server, GHRepositoryPath("upstream", "repo")), mockk(relaxed = true))
    forkMapping = GHGitRepositoryMapping(GHRepositoryCoordinates(server, GHRepositoryPath("fork-owner", "repo")), mockk(relaxed = true))
    account = GithubAccount(name = "user", server = server)

    val repositoriesManager = mockk<GHHostedRepositoriesManager> {
      every { knownRepositoriesState } returns MutableStateFlow(setOf(originMapping, forkMapping))
    }
    val connectionStateFlow = MutableStateFlow<GHRepositoryConnection?>(null)
    connectionManager = mockk<GHRepositoryConnectionManager> {
      every { connectionState } returns connectionStateFlow
      coEvery { openConnection(any(), any()) } answers {
        mockk<GHRepositoryConnection>(relaxed = true).also { connectionStateFlow.value = it }
      }
    }
    val connectedProjectVmFactory = mockk<GHPRConnectedProjectViewModelFactory>(relaxed = true)

    project.registerOrReplaceServiceInstance(GHHostedRepositoriesManager::class.java, repositoriesManager, disposable)
    project.registerOrReplaceServiceInstance(GHRepositoryConnectionManager::class.java, connectionManager, disposable)
    project.registerOrReplaceServiceInstance(GHPRConnectedProjectViewModelFactory::class.java, connectedProjectVmFactory, disposable)
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
    // await the full activateAndAwaitProject flow (not just the openConnection call) so no
    // part of its background coroutine is still running once tearDown() unmocks connectionManager
    actionInvoked.await()

    coVerify { connectionManager.openConnection(forkMapping, account) }
  }
}
