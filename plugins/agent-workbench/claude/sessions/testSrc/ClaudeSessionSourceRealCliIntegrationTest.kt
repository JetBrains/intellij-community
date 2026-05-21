// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionsStore
import com.intellij.agent.workbench.claude.sessions.backend.store.ClaudeStoreSessionBackend
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ClaudeSessionSourceRealCliIntegrationTest {
  @TempDir
  lateinit var tempDir: Path

  private val transcriptDirectoriesToDelete = ArrayList<Path>()

  @AfterEach
  fun cleanupClaudeTranscriptDirectories() {
    transcriptDirectoriesToDelete.forEach(::deleteRecursivelyIfExists)
    transcriptDirectoriesToDelete.clear()
    deleteRecursivelyIfExists(realCliProjectRoot(tempDir))
  }

  @Test
  fun realPrintCommandPersistsSessionAndListsItThroughSessionSource() {
    runBlocking(Dispatchers.IO) {
      val fixture = prepareRealClaudeSessionFixture(tempDir)
      transcriptDirectoriesToDelete.add(fixture.transcriptFile.parent)
      val thread = awaitThread(source = fixture.source, projectPath = fixture.projectDir.toString(), sessionId = fixture.sessionId)

      assertThat(thread)
        .withFailMessage(
          "Timed out waiting for Claude session source to surface session %s in %s. stdout=%s stderr=%s",
          fixture.sessionId,
          fixture.claudeHome,
          fixture.stdout.trim(),
          fixture.stderr.trim(),
        )
        .isNotNull
      assertThat(thread!!.id).isEqualTo(fixture.sessionId)
      assertThat(thread.title).isEqualTo(fixture.prompt)
      assertThat(thread.provider).isEqualTo(AgentSessionProvider.CLAUDE)
    }
  }

  @Test
  fun realArchiveAndUnarchiveRoundTripUsesInteractiveResumeCommand() {
    runBlocking(Dispatchers.IO) {
      val fixture = prepareRealClaudeSessionFixture(tempDir)
      transcriptDirectoriesToDelete.add(fixture.transcriptFile.parent)
      val archiver = PtyClaudeThreadRenameEngine(
        backend = fixture.backend,
        commandRunner = realHomeCommandRunner(),
      )

      assertThat(archiver.archiveThread(path = fixture.projectDir.toString(), threadId = fixture.sessionId))
        .withFailMessage("Claude archive command failed. stdout=%s stderr=%s", fixture.stdout.trim(), fixture.stderr.trim())
        .isTrue()
      assertThat(awaitLatestTranscriptTitle(fixture.transcriptFile, "[archived] ${fixture.prompt}"))
        .withFailMessage("Timed out waiting for transcript title after archive in %s", fixture.transcriptFile)
        .isTrue()
      assertThat(awaitThreadHidden(source = fixture.source, projectPath = fixture.projectDir.toString(), sessionId = fixture.sessionId))
        .isTrue()

      val archivedThread = awaitBackendThread(
        backend = fixture.backend,
        projectPath = fixture.projectDir.toString(),
        sessionId = fixture.sessionId,
      )
      assertThat(archivedThread).isNotNull
      assertThat(archivedThread!!.archived).isTrue()
      assertThat(archivedThread.title).isEqualTo(fixture.prompt)

      assertThat(archiver.unarchiveThread(path = fixture.projectDir.toString(), threadId = fixture.sessionId)).isTrue()
      assertThat(awaitLatestTranscriptTitle(fixture.transcriptFile, fixture.prompt))
        .withFailMessage("Timed out waiting for transcript title after unarchive in %s", fixture.transcriptFile)
        .isTrue()
      val restoredThread = awaitThread(source = fixture.source, projectPath = fixture.projectDir.toString(), sessionId = fixture.sessionId)
      assertThat(restoredThread).isNotNull
      assertThat(restoredThread!!.title).isEqualTo(fixture.prompt)
    }
  }

  @Test
  fun realArchiveRoundTripPreservesLongTitleViaNameFlag() {
    runBlocking(Dispatchers.IO) {
      val longPrompt = "Claude long title integration " +
                       "Resolve the current merge conflicts for this worktree. Determine the active conflicted files " +
                       "yourself using normal IDE tools, VCS integrations, or git commands. Use normal IDE tools, git " +
                       "workflow, file edits, and any installed skills. Success means this worktree leaves VCS conflict " +
                       "state for the current merge-related operation."
      val fixture = prepareRealClaudeSessionFixture(tempDir = tempDir, prompt = longPrompt)
      transcriptDirectoriesToDelete.add(fixture.transcriptFile.parent)
      val archiver = PtyClaudeThreadRenameEngine(
        backend = fixture.backend,
        commandRunner = realHomeCommandRunner(),
      )

      assertThat(archiver.archiveThread(path = fixture.projectDir.toString(), threadId = fixture.sessionId))
        .withFailMessage("Claude long-title archive failed. stdout=%s stderr=%s", fixture.stdout.trim(), fixture.stderr.trim())
        .isTrue()
      assertThat(awaitLatestTranscriptTitle(fixture.transcriptFile, "[archived] ${fixture.prompt}"))
        .withFailMessage("Timed out waiting for long-title transcript title after archive in %s", fixture.transcriptFile)
        .isTrue()

      assertThat(archiver.unarchiveThread(path = fixture.projectDir.toString(), threadId = fixture.sessionId)).isTrue()
      assertThat(awaitLatestTranscriptTitle(fixture.transcriptFile, fixture.prompt))
        .withFailMessage("Timed out waiting for long-title transcript title after unarchive in %s", fixture.transcriptFile)
        .isTrue()
      val restoredThread = awaitThread(source = fixture.source, projectPath = fixture.projectDir.toString(), sessionId = fixture.sessionId)
      assertThat(restoredThread).isNotNull
      assertThat(restoredThread!!.title).isEqualTo(fixture.prompt)
    }
  }

  @Test
  fun realRenameExistingThreadPreservesLongTitleViaNameFlag() {
    runBlocking(Dispatchers.IO) {
      val fixture = prepareRealClaudeSessionFixture(tempDir)
      transcriptDirectoriesToDelete.add(fixture.transcriptFile.parent)
      val longRenamedTitle = "Claude plain rename integration " +
                             "Validate that renaming an existing Claude Code thread preserves a very long title through " +
                             "the same interactive resume transport used by archive, without adding an archive prefix, " +
                             "without truncating the stored custom title, and without hiding the thread from active lists."
      val renamer = PtyClaudeThreadRenameEngine(
        backend = fixture.backend,
        commandRunner = realHomeCommandRunner(),
      )

      assertThat(renamer.rename(path = fixture.projectDir.toString(), threadId = fixture.sessionId, newTitle = longRenamedTitle))
        .withFailMessage("Claude long-title rename failed. stdout=%s stderr=%s", fixture.stdout.trim(), fixture.stderr.trim())
        .isTrue()
      assertThat(awaitLatestTranscriptTitle(fixture.transcriptFile, longRenamedTitle))
        .withFailMessage("Timed out waiting for long-title transcript title after rename in %s", fixture.transcriptFile)
        .isTrue()

      val renamedBackendThread = awaitBackendThread(
        backend = fixture.backend,
        projectPath = fixture.projectDir.toString(),
        sessionId = fixture.sessionId,
      )
      assertThat(renamedBackendThread).isNotNull
      assertThat(renamedBackendThread!!.archived).isFalse()
      assertThat(renamedBackendThread.title).isEqualTo(longRenamedTitle)

      val renamedThread = awaitThread(source = fixture.source, projectPath = fixture.projectDir.toString(), sessionId = fixture.sessionId)
      assertThat(renamedThread).isNotNull
      assertThat(renamedThread!!.title).isEqualTo(longRenamedTitle)
    }
  }
}

private data class RealClaudeSessionFixture(
  val claudeHome: Path,
  val projectDir: Path,
  val sessionId: String,
  val prompt: String,
  val stdout: String,
  val stderr: String,
  val backend: ClaudeStoreSessionBackend,
  val source: ClaudeSessionSource,
  val transcriptFile: Path,
)

private fun prepareRealClaudeSessionFixture(tempDir: Path, prompt: String? = null): RealClaudeSessionFixture {
  val claudeBinary = ClaudeCliSupport.findExecutable()
  assumeTrue(claudeBinary != null, "Claude CLI not found. Ensure `claude` is on PATH.")

  val userHome = System.getProperty("user.home")
  assumeTrue(!userHome.isNullOrBlank(), "Current terminal auth environment has no user.home")
  val claudeHome = Path.of(userHome, ".claude")

  val projectDir = realCliProjectRoot(tempDir).resolve("project")
  Files.createDirectories(projectDir)

  val sessionId = UUID.randomUUID().toString()
  val resolvedPrompt = prompt ?: "Claude real integration title ${sessionId.take(8)}"
  val processOutput = CapturingProcessHandler(
    GeneralCommandLine(
      checkNotNull(claudeBinary),
      "--print",
      PERMISSION_MODE_FLAG,
      PERMISSION_MODE_DEFAULT,
      "--session-id",
      sessionId,
      "--",
      resolvedPrompt,
    )
      .withWorkingDirectory(projectDir)
      .withEnvironment("DISABLE_AUTOUPDATER", "1")
  ).runProcess(180_000)

  assumeTrue(
    processOutput.exitCode == 0,
    buildString {
      appendLine("Claude CLI print command failed in the current terminal auth environment; skipping.")
      appendLine("exitCode=${processOutput.exitCode}")
      appendLine("stdout=${processOutput.stdout.trim()}")
      appendLine("stderr=${processOutput.stderr.trim()}")
    },
  )

  val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { claudeHome })
  val transcriptDirectory = ClaudeSessionsStore(claudeHomeProvider = { claudeHome }).findMatchingDirectories(projectDir.toString()).singleOrNull()
  assumeTrue(transcriptDirectory != null, "Claude did not create a project directory for the real session fixture")
  return RealClaudeSessionFixture(
    claudeHome = claudeHome,
    projectDir = projectDir,
    sessionId = sessionId,
    prompt = resolvedPrompt,
    stdout = processOutput.stdout,
    stderr = processOutput.stderr,
    backend = backend,
    source = ClaudeSessionSource(backend = backend),
    transcriptFile = checkNotNull(transcriptDirectory).resolve("$sessionId.jsonl"),
  )
}

private fun deleteRecursivelyIfExists(path: Path) {
  if (!Files.exists(path)) return
  Files.walk(path).use { stream ->
    stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
  }
}

private fun realCliProjectRoot(tempDir: Path): Path {
  return Path.of(System.getProperty("idea.home.path"), "out", "claude-real-cli-tests", tempDir.fileName.toString())
}

private suspend fun awaitThread(
  source: ClaudeSessionSource,
  projectPath: String,
  sessionId: String,
): AgentSessionThread? {
  var resolvedThread: AgentSessionThread? = null
  withTimeoutOrNull(30.seconds) {
    while (resolvedThread == null) {
      resolvedThread = source.listThreadsFromClosedProject(projectPath).firstOrNull { it.id == sessionId }
      if (resolvedThread == null) {
        delay(250.milliseconds)
      }
    }
  }
  return resolvedThread
}

private suspend fun awaitThreadHidden(source: ClaudeSessionSource, projectPath: String, sessionId: String): Boolean {
  var hidden = false
  withTimeoutOrNull(30.seconds) {
    while (!hidden) {
      hidden = source.listThreadsFromClosedProject(projectPath).none { it.id == sessionId }
      if (!hidden) {
        delay(250.milliseconds)
      }
    }
  }
  return hidden
}

private suspend fun awaitBackendThread(
  backend: ClaudeStoreSessionBackend,
  projectPath: String,
  sessionId: String,
): ClaudeBackendThread? {
  var resolvedThread: ClaudeBackendThread? = null
  withTimeoutOrNull(30.seconds) {
    while (resolvedThread == null) {
      resolvedThread = backend.listThreads(path = projectPath, openProject = null).firstOrNull { it.id == sessionId }
      if (resolvedThread == null) {
        delay(250.milliseconds)
      }
    }
  }
  return resolvedThread
}

private fun realHomeCommandRunner(): ClaudeThreadCommandRunner {
  return PtyClaudeThreadCommandRunner(
    environmentProvider = {
      HashMap(System.getenv()).apply {
        put("DISABLE_AUTOUPDATER", "1")
      }
    },
  )
}

private suspend fun awaitLatestTranscriptTitle(transcriptFile: Path, expectedTitle: String): Boolean {
  var matches = false
  withTimeoutOrNull(30.seconds) {
    while (!matches) {
      matches = latestTranscriptTitle(transcriptFile) == expectedTitle
      if (!matches) {
        delay(250.milliseconds)
      }
    }
  }
  return matches
}

private fun latestTranscriptTitle(transcriptFile: Path): String? {
  if (!Files.isRegularFile(transcriptFile)) {
    return null
  }
  var latest: String? = null
  for (line in Files.readAllLines(transcriptFile)) {
    latest = readJsonStringField(line, "agentName") ?: if (line.contains("\"type\":\"custom-title\"")) {
      readJsonStringField(line, "customTitle")
    }
    else {
      latest
    }
  }
  return latest
}

private fun readJsonStringField(line: String, fieldName: String): String? {
  val marker = "\"$fieldName\":\""
  val start = line.indexOf(marker)
  if (start < 0) return null
  val valueStart = start + marker.length
  val valueEnd = line.indexOf('"', valueStart)
  if (valueEnd <= valueStart) return null
  return line.substring(valueStart, valueEnd)
}
