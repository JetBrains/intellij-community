// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import junit.framework.TestCase

class AgentChatTabSelectionServiceTest : TestCase() {
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

    assertNotNull(selection)
    assertEquals("/work/project-a", selection?.projectPath)
    assertEquals("CODEX:thread-1", selection?.threadIdentity)
    assertEquals("thread-1", selection?.threadId)
    assertNull(selection?.subAgentId)
  }

  fun testMapReturnsNullForNonChatEditor() {
    assertNull(LightweightTestFileEditor(LightVirtualFile("notes.txt", "notes")).toAgentChatTabSelection())
    assertNull((null as FileEditor?).toAgentChatTabSelection())
  }

  fun testDetectOpenChatFiles() {
    val nonChatFiles = arrayOf<VirtualFile>(LightVirtualFile("notes.txt", "notes"))
    assertFalse(hasOpenAgentChatFiles(nonChatFiles))

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
    assertTrue(hasOpenAgentChatFiles(chatFiles))
  }
}
