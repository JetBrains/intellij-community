// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettingsContributors
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchPluginLoadingTest {
  @Test
  fun loadsAgentWorkbenchContentModulesAndRegistrations() {
    val fileSystem = VirtualFileManager.getInstance().getFileSystem("agent-chat")

    assertThat(fileSystem)
      .isNotNull
    assertThat(checkNotNull(fileSystem).javaClass.name)
      .isEqualTo("com.intellij.agent.workbench.chat.AgentChatVirtualFileSystem")
    assertThat(EditorTabColorProvider.EP_NAME.extensionList.map { it.javaClass.name })
      .contains("com.intellij.agent.workbench.chat.AgentChatEditorTabColorProvider")
    assertThat(AgentWorkbenchSettingsContributors.all().map { it.javaClass.name })
      .contains("com.intellij.agent.workbench.chat.AgentChatSettingsContributor")

    assertThat(ActionManager.getInstance().getAction(AgentWorkbenchActionIds.Sessions.OPEN_DEDICATED_FRAME))
      .isNotNull
    assertThat(ActionManager.getInstance().getAction(AgentWorkbenchActionIds.Sessions.EditorTab.PREVIOUS_PROPOSED_PLAN))
      .isNotNull
    assertThat(ActionManager.getInstance().getAction(AgentWorkbenchActionIds.Sessions.EditorTab.NEXT_PROPOSED_PLAN))
      .isNotNull
  }
}
