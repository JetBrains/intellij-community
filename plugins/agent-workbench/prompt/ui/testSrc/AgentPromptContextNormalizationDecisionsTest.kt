// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.array
import com.intellij.agent.workbench.prompt.core.number
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.core.string
import com.intellij.agent.workbench.prompt.ui.context.MANUAL_PROJECT_PATHS_SOURCE_ID
import com.intellij.agent.workbench.prompt.ui.context.ManualPathSelectionEntry
import com.intellij.agent.workbench.prompt.ui.context.buildManualPathsContextItem
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class AgentPromptContextNormalizationDecisionsTest {
  @Test
  fun removesFileItemWhenItMatchesCurrentProjectRoot() {
    val projectPath = systemPath("agent-workbench-current-root-file")
    val items = listOf(
      AgentPromptContextItem(
        rendererId = AgentPromptContextRendererIds.FILE,
        title = "File",
        body = projectPath,
        payload = AgentPromptPayload.obj("path" to AgentPromptPayload.str(projectPath)),
        source = "test",
      )
    )

    val normalized = normalizeContextItemsForProject(items, projectPath)

    assertThat(normalized).isEmpty()
  }

  @Test
  fun removesRelativeDotFileItemWhenProjectPathIsKnown() {
    val projectPath = systemPath("agent-workbench-current-root-dot")
    val item = AgentPromptContextItem(
      rendererId = AgentPromptContextRendererIds.FILE,
      title = "File",
      body = ".",
      source = "test",
    )

    val normalized = normalizeContextItemForProject(item, projectPath)

    assertThat(normalized).isNull()
  }

  @Test
  fun keepsRelativeDotFileItemWhenProjectPathIsUnknown() {
    val item = AgentPromptContextItem(
      rendererId = AgentPromptContextRendererIds.FILE,
      title = "File",
      body = ".",
      source = "test",
    )

    val normalized = normalizeContextItemForProject(item, null)

    assertThat(normalized).isEqualTo(item)
  }

  @Test
  fun removesCurrentProjectRootFromPathsItemAndKeepsRemainingEntries() {
    val projectPath = systemPath("agent-workbench-current-root-paths")
    val filePath = Path.of(projectPath, "src", "Main.kt").toString()
    val item = AgentPromptContextItem(
      rendererId = AgentPromptContextRendererIds.PATHS,
      title = "Paths",
      body = "dir: $projectPath\nfile: $filePath",
      payload = AgentPromptPayload.obj(
        "entries" to AgentPromptPayload.arr(
          AgentPromptPayload.obj(
            "kind" to AgentPromptPayload.str("dir"),
            "path" to AgentPromptPayload.str(projectPath),
          ),
          AgentPromptPayload.obj(
            "kind" to AgentPromptPayload.str("file"),
            "path" to AgentPromptPayload.str(filePath),
          ),
        ),
        "selectedCount" to AgentPromptPayload.num(2),
        "includedCount" to AgentPromptPayload.num(2),
        "directoryCount" to AgentPromptPayload.num(1),
        "fileCount" to AgentPromptPayload.num(1),
      ),
      source = "test",
    )

    val normalized = normalizeContextItemForProject(item, projectPath)

    assertThat(normalized).isNotNull()
    assertThat(normalized!!.body).isEqualTo("file: $filePath")
    assertThat(normalized.truncation.originalChars).isEqualTo(normalized.body.length)
    assertThat(normalized.truncation.includedChars).isEqualTo(normalized.body.length)

    val payload = normalized.payload.objOrNull()!!
    val entries = payload.array("entries")!!.map { value -> value.objOrNull()!! }
    assertThat(entries).hasSize(1)
    assertThat(entries.single().string("kind")).isEqualTo("file")
    assertThat(entries.single().string("path")).isEqualTo(filePath)
    assertThat(payload.number("selectedCount")).isEqualTo("1")
    assertThat(payload.number("includedCount")).isEqualTo("1")
    assertThat(payload.number("directoryCount")).isEqualTo("0")
    assertThat(payload.number("fileCount")).isEqualTo("1")
  }

  @Test
  fun dropsPathsItemWhenOnlyCurrentProjectRootRemains() {
    val projectPath = systemPath("agent-workbench-current-root-only")
    val item = AgentPromptContextItem(
      rendererId = AgentPromptContextRendererIds.PATHS,
      title = "Paths",
      body = "dir: $projectPath",
      payload = AgentPromptPayload.obj(
        "entries" to AgentPromptPayload.arr(
          AgentPromptPayload.obj(
            "kind" to AgentPromptPayload.str("dir"),
            "path" to AgentPromptPayload.str(projectPath),
          ),
        ),
        "selectedCount" to AgentPromptPayload.num(1),
        "includedCount" to AgentPromptPayload.num(1),
      ),
      source = "test",
    )

    val normalized = normalizeContextItemForProject(item, projectPath)

    assertThat(normalized).isNull()
  }

  @Test
  fun visibleEntriesKeepRawIdsUntilProjectPathIsKnown() {
    val projectPath = systemPath("agent-workbench-visible-root-paths")
    val filePath = Path.of(projectPath, "src", "Main.kt").toString()
    val rawEntries = listOf(
      ContextEntry(
        item = AgentPromptContextItem(
          rendererId = AgentPromptContextRendererIds.PATHS,
          title = "Paths",
          body = "dir: $projectPath\nfile: $filePath",
          payload = AgentPromptPayload.obj(
            "entries" to AgentPromptPayload.arr(
              AgentPromptPayload.obj(
                "kind" to AgentPromptPayload.str("dir"),
                "path" to AgentPromptPayload.str(projectPath),
              ),
              AgentPromptPayload.obj(
                "kind" to AgentPromptPayload.str("file"),
                "path" to AgentPromptPayload.str(filePath),
              ),
            ),
            "selectedCount" to AgentPromptPayload.num(2),
            "includedCount" to AgentPromptPayload.num(2),
            "directoryCount" to AgentPromptPayload.num(1),
            "fileCount" to AgentPromptPayload.num(1),
          ),
          source = "test",
        ),
        id = "auto:paths",
      )
    )

    val visibleWithoutProject = materializeVisibleContextEntries(rawEntries, emptyMap(), null)
    val visibleWithProject = materializeVisibleContextEntries(rawEntries, emptyMap(), projectPath)

    assertThat(visibleWithoutProject).hasSize(1)
    assertThat(visibleWithoutProject.single().id).isEqualTo("auto:paths")
    assertThat(visibleWithoutProject.single().origin).isEqualTo(ContextEntryOrigin.AUTO)
    assertThat(visibleWithoutProject.single().projectBasePath).isNull()
    assertThat(visibleWithoutProject.single().item.body).isEqualTo(rawEntries.single().item.body)

    assertThat(visibleWithProject).hasSize(1)
    assertThat(visibleWithProject.single().id).isEqualTo("auto:paths")
    assertThat(visibleWithProject.single().origin).isEqualTo(ContextEntryOrigin.AUTO)
    assertThat(visibleWithProject.single().projectBasePath).isEqualTo(projectPath)
    assertThat(visibleWithProject.single().item.body).isEqualTo("file: $filePath")
  }

  @Test
  fun manualProjectPathsMaterializeIntoSeparateVisibleEntries() {
    val projectPath = systemPath("agent-workbench-manual-visible-paths")
    val dirPath = Path.of(projectPath, "src").toString()
    val filePath = Path.of(projectPath, "src", "Main.kt").toString()
    val visibleEntries = materializeVisibleContextEntries(
      autoEntries = emptyList(),
      manualItemsBySourceId = linkedMapOf(
        MANUAL_PROJECT_PATHS_SOURCE_ID to listOf(buildManualPathsContextItem(
          listOf(
            ManualPathSelectionEntry(path = dirPath, isDirectory = true),
            ManualPathSelectionEntry(path = filePath, isDirectory = false),
          )
        ))
      ),
      projectPath = projectPath,
    )

    assertThat(visibleEntries).hasSize(2)
    assertThat(visibleEntries.map(ContextEntry::id)).containsExactly(
      manualPathContextEntryId(MANUAL_PROJECT_PATHS_SOURCE_ID, dirPath),
      manualPathContextEntryId(MANUAL_PROJECT_PATHS_SOURCE_ID, filePath),
    )
    assertThat(visibleEntries.map { it.item.body }).containsExactly(
      "dir: $dirPath",
      "file: $filePath",
    )
    assertThat(visibleEntries.map(ContextEntry::manualSourceId)).containsOnly(MANUAL_PROJECT_PATHS_SOURCE_ID)
    assertThat(visibleEntries.map(ContextEntry::origin)).containsOnly(ContextEntryOrigin.MANUAL)
  }

  @Test
  fun manualProjectPathsSplitAfterProjectRootNormalization() {
    val projectPath = systemPath("agent-workbench-manual-visible-filtered")
    val filePath = Path.of(projectPath, "src", "Main.kt").toString()
    val visibleEntries = materializeVisibleContextEntries(
      autoEntries = emptyList(),
      manualItemsBySourceId = linkedMapOf(
        MANUAL_PROJECT_PATHS_SOURCE_ID to listOf(buildManualPathsContextItem(
          listOf(
            ManualPathSelectionEntry(path = projectPath, isDirectory = true),
            ManualPathSelectionEntry(path = filePath, isDirectory = false),
          )
        ))
      ),
      projectPath = projectPath,
    )

    assertThat(visibleEntries).hasSize(1)
    assertThat(visibleEntries.single().id).isEqualTo(manualPathContextEntryId(MANUAL_PROJECT_PATHS_SOURCE_ID, filePath))
    assertThat(visibleEntries.single().item.body).isEqualTo("file: $filePath")
  }

  @Test
  fun manualScreenshotItemsMaterializeIntoSeparateVisibleEntries() {
    val sourceId = "manual.ui.context"
    val firstItem = AgentPromptContextItem(
      rendererId = AgentPromptContextRendererIds.SNIPPET,
      title = "Editor",
      body = "/tmp/ui-1.png",
      source = "manualScreenshot",
    )
    val secondItem = firstItem.copy(title = "Project View", body = "/tmp/ui-2.png")

    val visibleEntries = materializeVisibleContextEntries(
      autoEntries = emptyList(),
      manualItemsBySourceId = linkedMapOf(sourceId to listOf(firstItem, secondItem)),
      projectPath = null,
    )

    assertThat(visibleEntries).hasSize(2)
    assertThat(visibleEntries.map(ContextEntry::id)).containsExactly(
      manualContextEntryId(sourceId, firstItem),
      manualContextEntryId(sourceId, secondItem),
    )
    assertThat(visibleEntries.map(ContextEntry::backingItem)).containsExactly(firstItem, secondItem)
  }

  private fun systemPath(projectName: String): String {
    return FileUtil.toSystemDependentName(Path.of(SystemProperties.getUserHome(), projectName).toString())
  }
}
