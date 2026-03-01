// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.context.AgentPromptContextKinds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
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
      kindId = AgentPromptContextKinds.FILE,
      title = "File",
      content = filePath,
      projectBasePath = projectBasePath,
    )

    assertThat(entry.displayText).isEqualTo("File: src${File.separator}Main.kt")
  }

  @Test
  fun fileChipUsesDotForProjectRoot() {
    val home = systemPath(SystemProperties.getUserHome())
    val projectBasePath = systemPath("$home/agent-workbench-project-root")

    val entry = contextEntry(
      kindId = AgentPromptContextKinds.FILE,
      title = "File",
      content = projectBasePath,
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
      kindId = AgentPromptContextKinds.FILE,
      title = "File",
      content = filePath,
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
      kindId = AgentPromptContextKinds.PATHS,
      title = "Paths",
      content = "dir: $selectedDirectory",
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
      kindId = AgentPromptContextKinds.SYMBOL,
      title = "Symbol",
      content = absoluteContent,
      projectBasePath = projectBasePath,
    )

    assertThat(entry.displayText).isEqualTo("Symbol: $absoluteContent")
  }

  private fun contextEntry(
    kindId: String,
    title: String,
    content: String,
    projectBasePath: String?,
  ): ContextEntry {
    return ContextEntry(
      item = AgentPromptContextItem(
        kindId = kindId,
        title = title,
        content = content,
      ),
      projectBasePath = projectBasePath,
    )
  }

  private fun systemPath(path: String): String {
    return FileUtil.toSystemDependentName(path)
  }
}
