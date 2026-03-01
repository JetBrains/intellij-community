// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class CodexProjectSessionServiceTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun shutdownHookRunsOnCancellation() = runBlocking(Dispatchers.Default) {
    val parentJob = coroutineContext.job
    @Suppress("RAW_SCOPE_CREATION")
    val scope = CoroutineScope(coroutineContext + Job(parentJob))
    val shutdown = CompletableDeferred<Unit>()

    registerShutdownOnCancellation(scope) {
      shutdown.complete(Unit)
    }

    scope.cancel()

    withTimeout(1.seconds) {
      shutdown.await()
    }
  }

  @Test
  fun resolvesDirectoryFromIdeaMiscXml() {
    val projectRoot = tempDir.resolve("project-a")
    val ideaDir = projectRoot.resolve(".idea")
    Files.createDirectories(ideaDir)
    val miscXml = ideaDir.resolve("misc.xml")
    Files.writeString(miscXml, "<project />")

    val resolved = resolveProjectDirectory(
      recentProjectPath = miscXml,
      projectFilePath = null,
      basePath = null,
      guessedProjectDir = null,
    )

    assertThat(resolved).isEqualTo(projectRoot)
  }

  @Test
  fun resolvesDirectoryFromIprFilePath() {
    val projectRoot = tempDir.resolve("project-b")
    Files.createDirectories(projectRoot)
    val iprFile = projectRoot.resolve("project.ipr")
    Files.writeString(iprFile, "<project></project>")

    val resolved = resolveProjectDirectory(
      recentProjectPath = null,
      projectFilePath = iprFile.toString(),
      basePath = null,
      guessedProjectDir = null,
    )

    assertThat(resolved).isEqualTo(projectRoot)
  }

  @Test
  fun fallsBackToGuessedDirectoryWhenPathsMissing() {
    val projectRoot = tempDir.resolve("project-c")
    Files.createDirectories(projectRoot)

    val resolved = resolveProjectDirectory(
      recentProjectPath = null,
      projectFilePath = null,
      basePath = null,
      guessedProjectDir = projectRoot,
    )

    assertThat(resolved).isEqualTo(projectRoot)
  }
}
