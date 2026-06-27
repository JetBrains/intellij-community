// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/prompt-context/prompt-context-contracts.spec.md

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItemIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.SystemProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptContextEntryPathRenderingTest {
  @Test
  fun fileChipUsesProjectRelativePath() {
    val home = systemPath(SystemProperties.getUserHome())
    val projectBasePath = systemPath("$home/agent-workbench-project")
    val filePath = systemPath("$projectBasePath/src/Main.kt")

    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.FILE,
      title = "File",
      body = filePath,
      projectBasePath = projectBasePath,
    )

    assertThat(entry.displayText).isEqualTo("src${File.separator}Main.kt")
    assertThat(entry.tooltipText).isEqualTo("file: $filePath")
    assertThat(entry.tooltipText).doesNotContain("source=")
  }

  @Test
  fun fileChipUsesDotForProjectRoot() {
    val home = systemPath(SystemProperties.getUserHome())
    val projectBasePath = systemPath("$home/agent-workbench-project-root")

    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.FILE,
      title = "File",
      body = projectBasePath,
      projectBasePath = projectBasePath,
    )

    assertThat(entry.displayText).isEqualTo(".")
  }

  @Test
  fun fileChipUsesHomeRelativePathOutsideProject() {
    val home = systemPath(SystemProperties.getUserHome())
    val filePath = systemPath("$home/awb-chips/notes.md")
    val expected = FileUtil.getLocationRelativeToUserHome(filePath, false)

    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.FILE,
      title = "File",
      body = filePath,
      projectBasePath = systemPath("$home/other-project"),
    )

    assertThat(entry.displayText).isEqualTo(expected)
  }

  @Test
  fun fileChipMiddleTruncatesLongProjectRelativePath() {
    val home = systemPath(SystemProperties.getUserHome())
    val projectBasePath = systemPath("$home/agent-workbench-project-long")
    val relativePath = listOf(
      "very-long-source-root",
      "deeply-nested-package-name",
      "with-extra-context-for-preview",
      "and-even-more-structure",
      "VeryLongFileNameForPathChipRendering.kt",
    ).joinToString(File.separator)
    val filePath = systemPath("$projectBasePath/$relativePath")
    val expected = StringUtil.shortenPathWithEllipsis(relativePath, 60, true)

    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.FILE,
      title = "File",
      body = filePath,
      projectBasePath = projectBasePath,
    )

    assertThat(expected).contains("…")
    assertThat(entry.displayText).isEqualTo(expected)
    assertThat(entry.displayText).doesNotEndWith("…")
  }

  @Test
  fun pathsChipUsesShortenedPathWithoutPrefix() {
    val home = systemPath(SystemProperties.getUserHome())
    val projectBasePath = systemPath("$home/agent-workbench-project-paths")
    val selectedDirectory = systemPath("$projectBasePath/subdir")

    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.PATHS,
      title = "Paths",
      body = "dir: $selectedDirectory",
      projectBasePath = projectBasePath,
    )

    assertThat(entry.displayText).isEqualTo("subdir")
    assertThat(entry.tooltipText).isEqualTo("path: $selectedDirectory")
  }

  @Test
  fun pathsChipMiddleTruncatesLongHomeRelativePath() {
    val home = systemPath(SystemProperties.getUserHome())
    val selectedPath = systemPath(
      "$home/awb-long-selection/deeply-nested-selection/with-extra-context/for-preview/VeryLongSelectedPathName.txt"
    )
    val expected = StringUtil.shortenPathWithEllipsis(FileUtil.getLocationRelativeToUserHome(selectedPath, false), 60, true)

    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.PATHS,
      title = "Paths",
      body = "file: $selectedPath",
      projectBasePath = systemPath("$home/other-project"),
    )

    assertThat(expected).contains("…")
    assertThat(entry.displayText).isEqualTo(expected)
    assertThat(entry.displayText).doesNotEndWith("…")
  }

  @Test
  fun pathsChipFallsBackToTitleWhenNoPathPreviewExists() {
    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.PATHS,
      title = "Paths",
      body = "",
      projectBasePath = null,
    )

    assertThat(entry.displayText).isEqualTo("Paths")
  }

  @Test
  fun symbolChipUsesPreviewWithoutPrefix() {
    val preview = "com.example.SomeSymbol"

    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.SYMBOL,
      title = "Symbol",
      body = preview,
      projectBasePath = null,
    )

    assertThat(entry.displayText).isEqualTo(preview)
    assertThat(entry.displayText).doesNotStartWith("Symbol:")
    assertThat(entry.accessibleText).isEqualTo("Symbol: $preview")
  }

  @Test
  fun snippetChipShowsTitleOnly() {
    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.SNIPPET,
      title = "Selection (1-5)",
      body = "val x = foo.bar(baz)\nval y = 42",
      projectBasePath = null,
    )

    assertThat(entry.displayText).isEqualTo("Selection (1-5)")
    assertThat(entry.accessibleText).isEqualTo("Selection (1-5)")
  }

  @Test
  fun caretSnippetChipUsesCompactTitleButKeepsFullAccessibleTitle() {
    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.SNIPPET,
      title = "Caret Context (10-12)",
      body = "val x = foo.bar(baz)\nval y = 42",
      projectBasePath = null,
      payload = AgentPromptPayload.obj(
        "startLine" to AgentPromptPayload.num(10),
        "endLine" to AgentPromptPayload.num(12),
        "selection" to AgentPromptPayload.bool(false),
      ),
    )

    assertThat(entry.displayText).isEqualTo("Caret (10-12)")
    assertThat(entry.accessibleText).isEqualTo("Caret Context (10-12)")
    assertThat(entry.item.title).isEqualTo("Caret Context (10-12)")
  }

  @Test
  fun treeSelectionChipUsesCompactTitleButKeepsFullAccessibleTitle() {
    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.SNIPPET,
      title = "Tree Selection (Project)",
      body = "Tree: Project\nSelected:\n- src",
      projectBasePath = null,
      payload = AgentPromptPayload.obj("treeKind" to AgentPromptPayload.str("Project")),
      itemId = "tree.selection",
      source = "tree",
    )

    assertThat(entry.displayText).isEqualTo("Selection (Project)")
    assertThat(entry.accessibleText).isEqualTo("Tree Selection (Project)")
    assertThat(entry.item.title).isEqualTo("Tree Selection (Project)")
  }

  @Test
  fun localChangesChipUsesCompactTitleButKeepsFullAccessibleTitle() {
    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.SNIPPET,
      title = "Local Changes",
      body = "Default changelist\n- modified: src/Main.kt",
      projectBasePath = null,
      itemId = AgentPromptContextItemIds.CHANGES_SELECTION,
      source = "changes",
    )

    assertThat(entry.displayText).isEqualTo("Changes")
    assertThat(entry.accessibleText).isEqualTo("Local Changes")
    assertThat(entry.item.title).isEqualTo("Local Changes")
  }

  @Test
  fun vcsCommitsChipUsesFirstCommitFromPayload() {
    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.VCS_COMMITS,
      title = "Commits",
      body = "",
      projectBasePath = null,
      payload = AgentPromptPayload.obj(
        "entries" to AgentPromptPayload.arr(
          AgentPromptPayload.obj(
            "hash" to AgentPromptPayload.str("abc12345"),
            "rootPath" to AgentPromptPayload.str("/repo"),
          ),
        )
      ),
    )

    assertThat(entry.displayText).isEqualTo("abc12345")
    assertThat(entry.accessibleText).isEqualTo("Commits: abc12345")
    assertThat(entry.tooltipText).isEqualTo("commits:\nabc12345")
    assertThat(entry.tooltipText).doesNotContain("source=")
  }

  @Test
  fun unknownRendererTooltipFallsBackToGenericEnvelopeRender() {
    val entry = contextEntry(
      rendererId = "customRenderer",
      title = "Custom",
      body = "line 1",
      projectBasePath = null,
    )

    assertThat(entry.tooltipText).isEqualTo(
      "context: renderer=customRenderer title=Custom\n" +
      "```text\n" +
      "line 1\n" +
      "```"
    )
  }

  private fun contextEntry(
    rendererId: String,
    title: String,
    body: String,
    projectBasePath: String?,
    payload: AgentPromptPayloadValue = AgentPromptPayloadValue.Obj.EMPTY,
    itemId: String? = null,
    source: String = "test",
  ): ContextEntry {
    return ContextEntry(
      item = AgentPromptContextItem(
        rendererId = rendererId,
        title = title,
        body = body,
        payload = payload,
        itemId = itemId,
        source = source,
      ),
      projectBasePath = projectBasePath,
    )
  }

  private fun systemPath(path: String): String {
    return FileUtil.toSystemDependentName(path)
  }
}
