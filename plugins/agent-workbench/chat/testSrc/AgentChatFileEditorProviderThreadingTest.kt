// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.application.UI
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentChatFileEditorProviderThreadingTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val project get() = projectFixture.get()

  @Test
  fun createsUnavailableEditorForInvalidRestoredFileFromUiDispatcher(): Unit = timeoutRunBlocking {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-invalid",
      shellCommand = emptyList(),
      threadId = "thread-invalid",
      threadTitle = "Invalid thread",
      subAgentId = null,
    )
    val provider = AgentChatFileEditorProvider()

    val editor = withContext(Dispatchers.UI) {
      provider.createFileEditor(
        project = project,
        file = file,
        document = null,
        editorCoroutineScope = this,
      )
    }

    try {
      assertThat(editor.isValid).isFalse()
    }
    finally {
      Disposer.dispose(editor)
    }
  }
}
