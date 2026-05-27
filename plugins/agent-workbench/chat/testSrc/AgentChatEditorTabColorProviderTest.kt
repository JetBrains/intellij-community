// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.ide.RecentProjectColorPalette
import com.intellij.ide.RecentProjectColorInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.awt.Color

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentChatEditorTabColorProviderTest {
  private val project = ProjectManager.getInstance().defaultProject

  @Test
  fun indexedProjectColorUsesPlatformRecentProjectPalette() {
    val provider = colorProvider(
      colorInfo = { colorInfo(associatedIndex = 3) },
    )

    val color = provider.getEditorTabColor(project, chatFile())

    assertThat(color).isEqualTo(RecentProjectColorPalette.softBackground(3))
  }

  @Test
  fun customProjectColorUsesSoftenedColor() {
    val customColor = Color(0x1E, 0x88, 0xE5)
    val provider = colorProvider(
      colorInfo = { colorInfo(customColor = "1e88e5") },
    )

    val color = provider.getEditorTabColor(project, chatFile())

    assertThat(color).isNotNull
    assertThat(color).isNotEqualTo(customColor)
    assertThat(color).isNotEqualTo(RecentProjectColorPalette.softBackground(0))
  }

  @Test
  fun hiddenWhenProjectColorsAreDisabled() {
    val provider = colorProvider(
      isProjectColorsEnabled = { false },
      colorInfo = { colorInfo(associatedIndex = 3) },
    )

    assertThat(provider.getEditorTabColor(project, chatFile())).isNull()
  }

  @Test
  fun hiddenWhenAgentChatTabColoringIsDisabled() {
    val provider = colorProvider(
      isAgentChatTabColoringEnabled = { false },
      colorInfo = { colorInfo(associatedIndex = 3) },
    )

    assertThat(provider.getEditorTabColor(project, chatFile())).isNull()
  }

  @Test
  fun hiddenOutsideDedicatedFrame() {
    val provider = colorProvider(
      isDedicatedProject = { false },
      colorInfo = { colorInfo(associatedIndex = 3) },
    )

    assertThat(provider.getEditorTabColor(project, chatFile())).isNull()
  }

  @Test
  fun hiddenForNonChatFiles() {
    val provider = colorProvider(
      colorInfo = { colorInfo(associatedIndex = 3) },
    )

    assertThat(provider.getEditorTabColor(project, LightVirtualFile("notes.txt", "notes"))).isNull()
  }

  @Test
  fun hiddenWhenSourceProjectPathIsBlankInvalidOrUnknown() {
    var colorInfoLookups = 0
    val provider = colorProvider(
      colorInfo = {
        colorInfoLookups++
        null
      },
    )

    assertThat(provider.getEditorTabColor(project, chatFile(projectPath = ""))).isNull()
    assertThat(provider.getEditorTabColor(project, chatFile(projectPath = "bad\u0000path"))).isNull()
    assertThat(provider.getEditorTabColor(project, chatFile(projectPath = "/work/unknown"))).isNull()
    assertThat(colorInfoLookups).isEqualTo(1)
  }

  @Test
  fun hiddenWhenProjectColorMetadataIsInvalid() {
    assertThat(colorProvider(colorInfo = { colorInfo(associatedIndex = -1) }).getEditorTabColor(project, chatFile())).isNull()
    assertThat(colorProvider(colorInfo = { colorInfo(associatedIndex = 99) }).getEditorTabColor(project, chatFile())).isNull()
    assertThat(colorProvider(colorInfo = { colorInfo(customColor = "not-a-color") }).getEditorTabColor(project, chatFile())).isNull()
  }

  private fun colorProvider(
    isDedicatedProject: (Project) -> Boolean = { true },
    isProjectColorsEnabled: () -> Boolean = { true },
    isAgentChatTabColoringEnabled: () -> Boolean = { true },
    colorInfo: (String) -> RecentProjectColorInfo? = { colorInfo(associatedIndex = 0) },
  ): AgentChatEditorTabColorProvider {
    return AgentChatEditorTabColorProvider(
      isDedicatedProject = isDedicatedProject,
      isProjectColorsEnabled = isProjectColorsEnabled,
      isAgentChatTabColoringEnabled = isAgentChatTabColoringEnabled,
      sourceProjectColorInfo = colorInfo,
    )
  }

  private fun chatFile(projectPath: String = "/work/project-a"): AgentChatVirtualFile {
    return AgentChatVirtualFile(
      projectPath = projectPath,
      threadIdentity = "CODEX:thread-1",
      shellCommand = emptyList(),
      threadId = "thread-1",
      threadTitle = "Fix auth",
      subAgentId = null,
      threadActivity = AgentThreadActivity.READY,
    )
  }

  private fun colorInfo(
    associatedIndex: Int = -1,
    customColor: String? = null,
  ): RecentProjectColorInfo {
    return RecentProjectColorInfo().also {
      it.associatedIndex = associatedIndex
      it.customColor = customColor
    }
  }
}
