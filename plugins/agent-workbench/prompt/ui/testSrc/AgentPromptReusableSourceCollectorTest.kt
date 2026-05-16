// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AgentPromptReusableSourceCollectorTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun collectPromptFileEntriesReadsGithubPromptFiles() {
    val promptsDir = tempDir.resolve(".github/prompts")
    Files.createDirectories(promptsDir)
    Files.writeString(
      promptsDir.resolve("review.prompt.md"),
      """
      ---
      title: Review Changes
      description: Find bugs in the current diff
      ---
      Review the current changes and report concrete issues.
      """.trimIndent(),
    )

    val entries = collectPromptFileEntries(listOf(tempDir.toString()))

    assertThat(entries).hasSize(1)
    val entry = entries.single()
    assertThat(entry.label).isEqualTo("Review Changes")
    assertThat(entry.description).isEqualTo("Find bugs in the current diff")
    assertThat(entry.insertText).isEqualTo("Review the current changes and report concrete issues.")
    assertThat(entry.kind).isEqualTo(AgentPromptReusableSourceKind.PROMPT_FILE)
  }

  @Test
  fun collectPromptFileEntriesSkipsBlankPromptBodies() {
    val promptsDir = tempDir.resolve(".github/prompts")
    Files.createDirectories(promptsDir)
    Files.writeString(promptsDir.resolve("empty.prompt.md"), "---\ntitle: Empty\n---\n   ")

    assertThat(collectPromptFileEntries(listOf(tempDir.toString()))).isEmpty()
  }
}
