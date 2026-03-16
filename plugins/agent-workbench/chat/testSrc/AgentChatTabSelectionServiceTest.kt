// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import junit.framework.TestCase
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

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
    val selection = FakeEditor(chatFile).toAgentChatTabSelection()

    assertNotNull(selection)
    assertEquals("/work/project-a", selection?.projectPath)
    assertEquals("CODEX:thread-1", selection?.threadIdentity)
    assertEquals("thread-1", selection?.threadId)
    assertNull(selection?.subAgentId)
  }

  fun testMapReturnsNullForNonChatEditor() {
    assertNull(FakeEditor(LightVirtualFile("notes.txt", "notes")).toAgentChatTabSelection())
    assertNull((null as FileEditor?).toAgentChatTabSelection())
  }

  private class FakeEditor(private val virtualFile: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val component = JPanel()

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent = component

    override fun getName(): String = "FakeEditor"

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getFile(): VirtualFile = virtualFile

    override fun dispose() = Unit
  }
}
