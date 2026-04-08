// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.array
import com.intellij.agent.workbench.prompt.core.number
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

@TestApplication
class AgentPromptProjectPathsManualContextSourceTest {
  @Test
  fun resolvePickerBrowseRootPathsIncludesProjectAndScratchRoots() {
    val scopedRoots = resolvePickerBrowseRootPaths(
      contentRootPaths = listOf("/repo", "/other", "/repo"),
      scratchRootPaths = listOf("/scratches", "/extensions", "/scratches"),
    )

    assertThat(scopedRoots).containsExactly("/repo", "/other", "/scratches", "/extensions")
  }

  @Test
  fun resolvePickerBrowseRootPathsSupportsScratchOnlyProjects() {
    val scopedRoots = resolvePickerBrowseRootPaths(
      contentRootPaths = emptyList(),
      scratchRootPaths = listOf("/scratches", "/extensions"),
    )

    assertThat(scopedRoots).containsExactly("/scratches", "/extensions")
  }

  @Test
  fun resolveInitialManualPathSelectionKeepsExistingSelection() {
    val invocationFile = createPhysicalFile()
    val initialSelection = resolveInitialManualPathSelection(
      selection = listOf(ManualPathSelectionEntry(path = "/repo/existing.txt", isDirectory = false)),
      scopedRootPaths = listOf("/repo", invocationFile.parent.path),
    )

    assertThat(initialSelection).containsExactly(ManualPathSelectionEntry(path = "/repo/existing.txt", isDirectory = false))
  }

  @Test
  fun resolveInitialManualPathSelectionDoesNotSeedInvocationSelection() {
    val invocationFile = createPhysicalFile()
    val initialSelection = resolveInitialManualPathSelection(
      selection = emptyList(),
      scopedRootPaths = listOf(invocationFile.parent.path),
    )

    assertThat(initialSelection).isEmpty()
  }

  @Test
  fun resolveInitialTreePreselectionPrefersExistingSelectionOverInvocationContext() {
    val invocationFile = createPhysicalFile()
    val initialTreePreselection = resolveInitialTreePreselection(
      initialSelection = listOf(ManualPathSelectionEntry(path = "/repo/existing.txt", isDirectory = false)),
      invocationData = invocationData(SimpleDataContext.builder().add(CommonDataKeys.VIRTUAL_FILE, invocationFile).build()),
      scopedRootPaths = listOf("/repo", invocationFile.parent.path),
    )

    assertThat(initialTreePreselection).isNull()
  }

  @Test
  fun resolveInitialTreePreselectionFallsBackToInvocationDataSelection() {
    val invocationFile = createPhysicalFile()
    val initialTreePreselection = resolveInitialTreePreselection(
      initialSelection = emptyList(),
      invocationData = invocationData(SimpleDataContext.builder().add(CommonDataKeys.VIRTUAL_FILE, invocationFile).build()),
      scopedRootPaths = listOf(invocationFile.parent.path),
    )

    assertThat(initialTreePreselection).isEqualTo(ManualPathSelectionEntry(path = invocationFile.path, isDirectory = false))
  }

  @Test
  fun extractCurrentManualPathSelectionUsesFirstAttachableFileFromDataContextArray() {
    val invocationFile = createPhysicalFile()
    val selected = extractCurrentManualPathSelection(
      invocationData = invocationData(
        SimpleDataContext.builder()
          .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(LightVirtualFile("temp.txt", ""), invocationFile))
          .build()
      ),
      scopedRootPaths = listOf(invocationFile.parent.path),
    )

    assertThat(selected).isEqualTo(ManualPathSelectionEntry(path = invocationFile.path, isDirectory = false))
  }

  @Test
  fun extractCurrentManualPathSelectionSupportsDirectories() {
    val invocationDirectory = createPhysicalDirectory()
    val selected = extractCurrentManualPathSelection(
      invocationData = invocationData(SimpleDataContext.builder().add(CommonDataKeys.VIRTUAL_FILE, invocationDirectory).build()),
      scopedRootPaths = listOf(invocationDirectory.parent.path),
    )

    assertThat(selected).isEqualTo(ManualPathSelectionEntry(path = invocationDirectory.path, isDirectory = true))
  }

  @Test
  fun extractCurrentManualPathSelectionFallsBackToSingleFileWhenArrayHasNoAttachableEntries() {
    val invocationFile = createPhysicalFile()
    val selected = extractCurrentManualPathSelection(
      invocationData = invocationData(
        SimpleDataContext.builder()
          .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(LightVirtualFile("temp.txt", "")))
          .add(CommonDataKeys.VIRTUAL_FILE, invocationFile)
          .build()
      ),
      scopedRootPaths = listOf(invocationFile.parent.path),
    )

    assertThat(selected).isEqualTo(ManualPathSelectionEntry(path = invocationFile.path, isDirectory = false))
  }

  @Test
  fun extractCurrentManualPathSelectionSkipsOutOfScopeFiles() {
    val invocationFile = createPhysicalFile()
    val selected = extractCurrentManualPathSelection(
      invocationData = invocationData(SimpleDataContext.builder().add(CommonDataKeys.VIRTUAL_FILE, invocationFile).build()),
      scopedRootPaths = listOf("/repo"),
    )

    assertThat(selected).isNull()
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

  private fun invocationData(dataContext: DataContext): AgentPromptInvocationData {
    return AgentPromptInvocationData(
      project = ProjectManager.getInstance().defaultProject,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent",
      actionPlace = "EditorPopup",
      invokedAtMs = 0L,
      attributes = mapOf(AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY to dataContext),
    )
  }

  private fun createPhysicalFile(): VirtualFile {
    val nioPath = Files.createTempFile("aw-manual-path-selection", ".txt")
    Files.writeString(nioPath, "content")
    return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(nioPath))
  }

  private fun createPhysicalDirectory(): VirtualFile {
    val nioPath = Files.createTempDirectory("aw-manual-path-selection-dir")
    return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(nioPath))
  }
}
