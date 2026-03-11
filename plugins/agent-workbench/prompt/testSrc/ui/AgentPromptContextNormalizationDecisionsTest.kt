// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayload
import com.intellij.agent.workbench.sessions.core.prompt.array
import com.intellij.agent.workbench.sessions.core.prompt.number
import com.intellij.agent.workbench.sessions.core.prompt.objOrNull
import com.intellij.agent.workbench.sessions.core.prompt.string
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

  private fun systemPath(projectName: String): String {
    return FileUtil.toSystemDependentName(Path.of(SystemProperties.getUserHome(), projectName).toString())
  }
}
