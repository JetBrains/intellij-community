// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentChatTabSelectionServiceTest {
  @Test
  fun testMapChatEditorToSelection() {
    val chatFile = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-1",
      shellCommand = emptyList(),
      threadId = "thread-1",
      threadTitle = "Fix auth",
      subAgentId = null,
    )
    val selection = LightweightTestFileEditor(chatFile).toAgentChatTabSelection()

    assertThat(selection).isNotNull
    assertThat(selection?.projectPath).isEqualTo("/work/project-a")
    assertThat(selection?.threadIdentity).isEqualTo("CODEX:thread-1")
    assertThat(selection?.threadId).isEqualTo("thread-1")
    assertThat(selection?.subAgentId).isNull()
  }

  @Test
  fun testMapReturnsNullForNonChatEditor() {
    assertThat(LightweightTestFileEditor(LightVirtualFile("notes.txt", "notes")).toAgentChatTabSelection()).isNull()
    assertThat((null as FileEditor?).toAgentChatTabSelection()).isNull()
  }

  @Test
  fun testDetectOpenChatFiles() {
    val nonChatFiles = arrayOf<VirtualFile>(LightVirtualFile("notes.txt", "notes"))
    assertThat(hasOpenAgentChatFiles(nonChatFiles)).isFalse()

    val chatFiles = arrayOf<VirtualFile>(
      LightVirtualFile("notes.txt", "notes"),
      AgentChatVirtualFile(
        projectPath = "/work/project-a",
        threadIdentity = "CODEX:thread-1",
        shellCommand = emptyList(),
        threadId = "thread-1",
        threadTitle = "Fix auth",
        subAgentId = null,
      ),
    )
    assertThat(hasOpenAgentChatFiles(chatFiles)).isTrue()
  }
}
