// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentThreadViewTabSelectionServiceTest {
  @Test
  fun testMapThreadViewEditorToSelection() {
    val threadViewFile = AgentThreadViewVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-1",
      shellCommand = emptyList(),
      threadId = "thread-1",
      threadTitle = "Fix auth",
      subAgentId = null,
    )
    val selection = LightweightTestFileEditor(threadViewFile).toAgentThreadViewTabSelection()

    assertThat(selection).isNotNull
    assertThat(selection?.projectPath).isEqualTo("/work/project-a")
    assertThat(selection?.threadIdentity).isEqualTo("CODEX:thread-1")
    assertThat(selection?.threadId).isEqualTo("thread-1")
    assertThat(selection?.subAgentId).isNull()
  }

  @Test
  fun testMapReturnsNullForNonThreadViewEditor() {
    assertThat(LightweightTestFileEditor(LightVirtualFile("notes.txt", "notes")).toAgentThreadViewTabSelection()).isNull()
    assertThat((null as FileEditor?).toAgentThreadViewTabSelection()).isNull()
  }

  @Test
  fun testDetectOpenThreadViewFiles() {
    val nonThreadViewFiles = arrayOf<VirtualFile>(LightVirtualFile("notes.txt", "notes"))
    assertThat(hasOpenAgentThreadViewFiles(nonThreadViewFiles)).isFalse()

    val threadViewFiles = arrayOf<VirtualFile>(
      LightVirtualFile("notes.txt", "notes"),
      AgentThreadViewVirtualFile(
        projectPath = "/work/project-a",
        threadIdentity = "CODEX:thread-1",
        shellCommand = emptyList(),
        threadId = "thread-1",
        threadTitle = "Fix auth",
        subAgentId = null,
      ),
    )
    assertThat(hasOpenAgentThreadViewFiles(threadViewFiles)).isTrue()
  }
}
