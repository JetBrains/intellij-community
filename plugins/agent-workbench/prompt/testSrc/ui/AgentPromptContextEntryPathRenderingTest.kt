// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayload
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayloadValue
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.SystemProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
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

    assertThat(entry.displayText).isEqualTo("File: src${File.separator}Main.kt")
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

    assertThat(entry.displayText).isEqualTo("File: .")
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

    assertThat(entry.displayText).isEqualTo("File: $expected")
  }

  @Test
  fun pathsChipKeepsPrefixAndShortensPath() {
    val home = systemPath(SystemProperties.getUserHome())
    val projectBasePath = systemPath("$home/agent-workbench-project-paths")
    val selectedDirectory = systemPath("$projectBasePath/subdir")

    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.PATHS,
      title = "Paths",
      body = "dir: $selectedDirectory",
      projectBasePath = projectBasePath,
    )

    assertThat(entry.displayText).isEqualTo("Paths: dir: subdir")
  }

  @Test
  fun nonPathKindsDoNotShortenAbsoluteContent() {
    val home = systemPath(SystemProperties.getUserHome())
    val projectBasePath = systemPath("$home/agent-workbench-project-symbol")
    val absoluteContent = systemPath("$home/agent-workbench-project-symbol/src/SomeSymbol")

    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.SYMBOL,
      title = "Symbol",
      body = absoluteContent,
      projectBasePath = projectBasePath,
    )

    assertThat(entry.displayText).isEqualTo("Symbol: $absoluteContent")
  }

  @Test
  fun vcsRevisionsChipUsesFirstRevisionFromPayload() {
    val entry = contextEntry(
      rendererId = AgentPromptContextRendererIds.VCS_REVISIONS,
      title = "VCS Revisions",
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

    assertThat(entry.displayText).isEqualTo("VCS Revisions: abc12345")
  }

  private fun contextEntry(
    rendererId: String,
    title: String,
    body: String,
    projectBasePath: String?,
    payload: AgentPromptPayloadValue = AgentPromptPayloadValue.Obj.EMPTY,
  ): ContextEntry {
    return ContextEntry(
      item = AgentPromptContextItem(
        rendererId = rendererId,
        title = title,
        body = body,
        payload = payload,
        source = "test",
      ),
      projectBasePath = projectBasePath,
    )
  }

  private fun systemPath(path: String): String {
    return FileUtil.toSystemDependentName(path)
  }
}
