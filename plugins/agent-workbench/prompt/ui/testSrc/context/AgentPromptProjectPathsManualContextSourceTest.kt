// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.array
import com.intellij.agent.workbench.prompt.core.number
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentPromptProjectPathsManualContextSourceTest {
  @Test
  fun resolveScopedContentRootPathsPrefersWorkingProjectPathWithinContent() {
    val scopedRoots = resolveScopedContentRootPaths(
      contentRootPaths = listOf("/repo", "/other"),
      workingProjectPath = "/repo/project-a",
    )

    assertThat(scopedRoots).containsExactly("/repo/project-a")
  }

  @Test
  fun resolveScopedContentRootPathsFallsBackToAllContentRootsWhenWorkingProjectPathIsOutsideContent() {
    val scopedRoots = resolveScopedContentRootPaths(
      contentRootPaths = listOf("/repo", "/other"),
      workingProjectPath = "/unknown/project",
    )

    assertThat(scopedRoots).containsExactlyInAnyOrder("/repo", "/other")
  }

  @Test
  fun resolvePickerRootPathsAlwaysIncludesScratchAndExtensionRoots() {
    val scopedRoots = resolvePickerRootPaths(
      contentRootPaths = listOf("/repo", "/other"),
      scratchRootPaths = listOf("/scratches", "/extensions"),
      workingProjectPath = "/repo/project-a",
    )

    assertThat(scopedRoots).containsExactly("/repo/project-a", "/scratches", "/extensions")
  }

  @Test
  fun resolvePickerRootPathsSupportsScratchOnlyProjects() {
    val scopedRoots = resolvePickerRootPaths(
      contentRootPaths = emptyList(),
      scratchRootPaths = listOf("/scratches", "/extensions"),
      workingProjectPath = "/repo/project-a",
    )

    assertThat(scopedRoots).containsExactly("/scratches", "/extensions")
  }

  @Test
  fun filterManualPathSelectionToScopedRootsDropsEntriesOutsideScope() {
    val filtered = filterManualPathSelectionToScopedRoots(
      selection = listOf(
        ManualPathSelectionEntry(path = "/repo/project/src/Main.kt", isDirectory = false),
        ManualPathSelectionEntry(path = "/repo/project/src", isDirectory = true),
        ManualPathSelectionEntry(path = "/repo/other/Other.kt", isDirectory = false),
      ),
      scopedRootPaths = listOf("/repo/project"),
    )

    assertThat(filtered).containsExactly(
      ManualPathSelectionEntry(path = "/repo/project/src/Main.kt", isDirectory = false),
      ManualPathSelectionEntry(path = "/repo/project/src", isDirectory = true),
    )
  }

  @Test
  fun filterManualPathSelectionToScopedRootsKeepsScratchAndExtensionEntriesWithinScope() {
    val filtered = filterManualPathSelectionToScopedRoots(
      selection = listOf(
        ManualPathSelectionEntry(path = "/repo/project/src/Main.kt", isDirectory = false),
        ManualPathSelectionEntry(path = "/scratches/notes.http", isDirectory = false),
        ManualPathSelectionEntry(path = "/extensions/com.intellij.agent.workbench/rules", isDirectory = true),
        ManualPathSelectionEntry(path = "/outside/file.txt", isDirectory = false),
      ),
      scopedRootPaths = listOf("/repo/project", "/scratches", "/extensions"),
    )

    assertThat(filtered).containsExactly(
      ManualPathSelectionEntry(path = "/repo/project/src/Main.kt", isDirectory = false),
      ManualPathSelectionEntry(path = "/scratches/notes.http", isDirectory = false),
      ManualPathSelectionEntry(path = "/extensions/com.intellij.agent.workbench/rules", isDirectory = true),
    )
  }

  @Test
  fun manualPathSelectionStateAddSearchSelectionKeepsExistingSelectionWhenAddingAnotherFile() {
    val state = ManualPathSelectionState(
      listOf(ManualPathSelectionEntry(path = "/repo/project/existing.txt", isDirectory = false))
    )

    state.addSearchSelection(
      listOf(
        ManualPathSelectionEntry(path = "/repo/project/new.txt", isDirectory = false),
      )
    )

    assertThat(state.snapshot()).containsExactly(
      ManualPathSelectionEntry(path = "/repo/project/existing.txt", isDirectory = false),
      ManualPathSelectionEntry(path = "/repo/project/new.txt", isDirectory = false),
    )
  }

  @Test
  fun manualPathSelectionStateAddTreeSelectionAppendsToExistingSelection() {
    val state = ManualPathSelectionState(
      listOf(
        ManualPathSelectionEntry(path = "/repo/project/keep.txt", isDirectory = false),
      )
    )

    state.addTreeSelection(
      listOf(
        ManualPathSelectionEntry(path = "/repo/project/src", isDirectory = true),
        ManualPathSelectionEntry(path = "/repo/project/add.txt", isDirectory = false),
      )
    )

    assertThat(state.snapshot()).containsExactly(
      ManualPathSelectionEntry(path = "/repo/project/keep.txt", isDirectory = false),
      ManualPathSelectionEntry(path = "/repo/project/src", isDirectory = true),
      ManualPathSelectionEntry(path = "/repo/project/add.txt", isDirectory = false),
    )
  }

  @Test
  fun removeManualPathSelectionDropsOnlyExplicitlyRemovedPaths() {
    val remaining = removeManualPathSelection(
      selection = listOf(
        ManualPathSelectionEntry(path = "/repo/project/keep.txt", isDirectory = false),
        ManualPathSelectionEntry(path = "/repo/project/remove.txt", isDirectory = false),
        ManualPathSelectionEntry(path = "/repo/project/src", isDirectory = true),
      ),
      removedSelection = listOf(
        ManualPathSelectionEntry(path = "/repo/project/remove.txt", isDirectory = false),
      ),
    )

    assertThat(remaining).containsExactly(
      ManualPathSelectionEntry(path = "/repo/project/keep.txt", isDirectory = false),
      ManualPathSelectionEntry(path = "/repo/project/src", isDirectory = true),
    )
  }

  @Test
  fun buildManualPathsContextItemCapsSelectionAndPreservesPayloadShape() {
    val selection = (1..22).map { index ->
      ManualPathSelectionEntry(
        path = "/repo/${if (index % 5 == 0) "dir-$index" else "file-$index.txt"}",
        isDirectory = index % 5 == 0,
      )
    }

    val item = buildManualPathsContextItem(selection)

    assertThat(item.title).isEqualTo(AgentPromptBundle.message("manual.context.paths.title"))
    assertThat(item.itemId).isEqualTo("manual.project.paths")
    assertThat(item.source).isEqualTo("manualPaths")
    assertThat(item.truncation.reason).isEqualTo(AgentPromptContextTruncationReason.SOURCE_LIMIT)
    assertThat(item.body.lineSequence().toList()).hasSize(20)
    assertThat(item.payload.objOrNull()?.array("entries")).hasSize(20)
    assertThat(item.payload.objOrNull()?.number("selectedCount")).isEqualTo("22")
    assertThat(item.payload.objOrNull()?.number("includedCount")).isEqualTo("20")
    assertThat(item.payload.objOrNull()?.number("directoryCount")).isEqualTo("4")
    assertThat(item.payload.objOrNull()?.number("fileCount")).isEqualTo("16")
  }

  @Test
  fun extractCurrentPathsReadsExistingPayloadEntries() {
    val item = buildManualPathsContextItem(
      listOf(
        ManualPathSelectionEntry(path = "/repo/src/Main.kt", isDirectory = false),
        ManualPathSelectionEntry(path = "/repo/src", isDirectory = true),
      )
    )

    assertThat(extractCurrentPaths(item)).containsExactly(
      ManualPathSelectionEntry(path = "/repo/src/Main.kt", isDirectory = false),
      ManualPathSelectionEntry(path = "/repo/src", isDirectory = true),
    )
  }

  @Test
  fun normalizeManualPathSelectionDeduplicatesAndTrimsPaths() {
    val normalized = normalizeManualPathSelection(
      listOf(
        ManualPathSelectionEntry(path = "  /repo/src/Main.kt  ", isDirectory = false),
        ManualPathSelectionEntry(path = "/repo/src/Main.kt", isDirectory = true),
        ManualPathSelectionEntry(path = "", isDirectory = false),
        ManualPathSelectionEntry(path = "/repo/src", isDirectory = true),
      )
    )

    assertThat(normalized).containsExactly(
      ManualPathSelectionEntry(path = "/repo/src/Main.kt", isDirectory = false),
      ManualPathSelectionEntry(path = "/repo/src", isDirectory = true),
    )
  }
}
