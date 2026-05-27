// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.service.AgentSessionOpenProjectLoadTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionPrefetchedThreads
import com.intellij.agent.workbench.sessions.service.AgentSessionThreadLoadSupport
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class AgentSessionThreadLoadSupportTest {
  private val codexProvider = AgentSessionProvider.from("codex")
  private val terminalProvider = AgentSessionProvider.from("terminal")

  @Test
  fun loadSourcesIncrementallyFallsBackToOpenProjectLoadWhenPrefetchedDirectoryDiffers() {
    runBlocking(Dispatchers.Default) {
      val identityPath = "/home/haze/work/ultimate/toolbox/toolbox.bazelproject"
      val projectDirectory = "/home/haze/work/ultimate"
      val loadedPaths = mutableListOf<String>()
      val source = CwdBackedScriptedSessionSource(
        provider = codexProvider,
        list = { path, _ ->
          loadedPaths += path
          listOf(thread(id = "codex-open", updatedAt = 200L, provider = codexProvider))
        },
      )

      val result = threadLoadSupport(source).loadSourcesIncrementally(
        sessionSources = listOf(source),
        loadTarget = AgentSessionOpenProjectLoadTarget(
          identityPath = identityPath,
          projectDirectory = projectDirectory,
          project = openProjectProxy(name = "ultimate (toolbox)", basePath = projectDirectory),
          originalPath = identityPath,
        ),
        prefetchedByProvider = mapOf(
          codexProvider to mapOf(
            identityPath to AgentSessionPrefetchedThreads(
              projectDirectory = null,
              threads = emptyList(),
            )
          )
        ),
        onPartialResult = { _, _ -> },
      )

      assertThat(loadedPaths).containsExactly(projectDirectory)
      assertThat(result.threads.map { it.id }).containsExactly("codex-open")
    }
  }

  @Test
  fun loadSourcesIncrementallyUsesProjectDirectoryWhenKnown() {
    runBlocking(Dispatchers.Default) {
      val identityPath = "/home/haze/work/ultimate/toolbox/toolbox.bazelproject"
      val projectDirectory = "/home/haze/work/ultimate"
      val loadedPaths = mutableListOf<String>()
      val source = ScriptedSessionSource(
        provider = terminalProvider,
        listFromOpenProject = { path, _ ->
          loadedPaths += path
          listOf(thread(id = "terminal-open", updatedAt = 200L, provider = terminalProvider))
        },
      )

      val result = threadLoadSupport(source).loadSourcesIncrementally(
        sessionSources = listOf(source),
        loadTarget = AgentSessionOpenProjectLoadTarget(
          identityPath = identityPath,
          projectDirectory = projectDirectory,
          project = openProjectProxy(name = "ultimate (toolbox)", basePath = projectDirectory),
          originalPath = identityPath,
        ),
        prefetchedByProvider = emptyMap(),
        onPartialResult = { _, _ -> },
      )

      assertThat(loadedPaths).containsExactly(projectDirectory)
      assertThat(result.threads.map { it.id }).containsExactly("terminal-open")
    }
  }

  @Test
  fun loadThreadsFromClosedProjectUsesProjectDirectoryWhenKnown() {
    runBlocking(Dispatchers.Default) {
      val identityPath = "/home/haze/work/ultimate/toolbox/toolbox.bazelproject"
      val projectDirectory = "/home/haze/work/ultimate"
      val loadedPaths = mutableListOf<String>()
      val source = ScriptedSessionSource(
        provider = terminalProvider,
        listFromClosedProject = { path ->
          loadedPaths += path
          listOf(thread(id = "terminal-closed", updatedAt = 200L, provider = terminalProvider))
        },
      )

      val result = threadLoadSupport(source).loadThreadsFromClosedProject(
        path = identityPath,
        projectDirectory = projectDirectory,
      )

      assertThat(loadedPaths).containsExactly(projectDirectory)
      assertThat(result.threads.map { it.id }).containsExactly("terminal-closed")
    }
  }

  @Test
  fun loadSourcesIncrementallyReusesPrefetchedThreadsWhenDirectoryMatches() {
    runBlocking(Dispatchers.Default) {
      val projectPath = "/work/project-a"
      val openLoadCalls = AtomicInteger(0)
      val prefetchedThread = thread(id = "codex-prefetched", updatedAt = 100L, provider = codexProvider)
      val source = ScriptedSessionSource(
        provider = codexProvider,
        listFromOpenProject = { _, _ ->
          openLoadCalls.incrementAndGet()
          listOf(thread(id = "codex-open", updatedAt = 200L, provider = codexProvider))
        },
      )

      val result = threadLoadSupport(source).loadSourcesIncrementally(
        sessionSources = listOf(source),
        loadTarget = AgentSessionOpenProjectLoadTarget(
          identityPath = projectPath,
          projectDirectory = projectPath,
          project = openProjectProxy(name = "Project A", basePath = projectPath),
          originalPath = projectPath,
        ),
        prefetchedByProvider = mapOf(
          codexProvider to mapOf(
            projectPath to AgentSessionPrefetchedThreads(
              projectDirectory = projectPath,
              threads = listOf(prefetchedThread),
            )
          )
        ),
        onPartialResult = { _, _ -> },
      )

      assertThat(openLoadCalls.get()).isZero()
      assertThat(result.threads.map { it.id }).containsExactly(prefetchedThread.id)
    }
  }

  private fun threadLoadSupport(source: AgentSessionSource): AgentSessionThreadLoadSupport {
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = source.provider,
      supportedModes = emptySet(),
      cliAvailable = true,
    )
    return AgentSessionThreadLoadSupport(
      sessionSourcesProvider = { listOf(source) },
      applyArchiveSuppressions = { _, _, threads -> threads },
      resolveErrorMessage = { _, throwable -> throwable.message.orEmpty() },
      resolveProviderWarningMessage = { _, throwable -> throwable.message.orEmpty() },
      providerDescriptorProvider = { provider -> descriptor.takeIf { it.provider == provider } },
    )
  }
}
