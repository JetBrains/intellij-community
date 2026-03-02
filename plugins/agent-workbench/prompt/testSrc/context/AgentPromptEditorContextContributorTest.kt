// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.bool
import com.intellij.agent.workbench.sessions.core.prompt.number
import com.intellij.agent.workbench.sessions.core.prompt.objOrNull
import com.intellij.agent.workbench.sessions.core.prompt.string
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentPromptEditorContextContributorTest {
  private val contributor = AgentPromptEditorContextContributor()

  @Test
  fun returnsEmptyWhenInvocationHasNoEditor() {
    val project = ProjectManager.getInstance().defaultProject
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .build()

    val result = contributor.collect(invocationData(project = project, dataContext = dataContext))

    assertThat(result).isEmpty()
  }

  @Test
  fun buildsSnippetAndFileItemsFromEditorInvocation() {
    val project = ProjectManager.getInstance().defaultProject
    runInEdtAndWait {
      val editorFactory = EditorFactory.getInstance()
      val document = editorFactory.createDocument(
        """
        fun answer(): Int {
          return 42
        }
        """.trimIndent()
      )
      val editor = editorFactory.createEditor(document)
      try {
        editor.caretModel.moveToOffset(document.text.indexOf("return"))
        val file = LightVirtualFile("Sample.kt", document.text)
        val dataContext = SimpleDataContext.builder()
          .add(CommonDataKeys.EDITOR, editor)
          .add(CommonDataKeys.VIRTUAL_FILE, file)
          .build()

        val result = contributor.collect(invocationData(project = project, dataContext = dataContext))

        assertThat(result.map { it.rendererId }).containsExactly(
          AgentPromptContextRendererIds.FILE,
          AgentPromptContextRendererIds.SNIPPET,
        )
        val snippetItem = result.first { it.rendererId == AgentPromptContextRendererIds.SNIPPET }
        val snippetPayload = snippetItem.payload.objOrNull()!!
        assertThat(snippetPayload.number("startLine")).isNotBlank()
        assertThat(snippetPayload.number("endLine")).isNotBlank()
        assertThat(snippetPayload.bool("selection")).isNotNull
        assertThat(snippetItem.source).isEqualTo("editor")
        assertThat(snippetItem.truncation.reason).isEqualTo(AgentPromptContextTruncationReason.NONE)
        assertThat(snippetItem.truncation.originalChars).isEqualTo(snippetItem.body.length)
        assertThat(snippetItem.truncation.includedChars).isEqualTo(snippetItem.body.length)
        val fileItem = result.first { it.rendererId == AgentPromptContextRendererIds.FILE }
        assertThat(fileItem.body).contains("Sample.kt")
        assertThat(fileItem.source).isEqualTo("editor")
      }
      finally {
        editorFactory.releaseEditor(editor)
      }
    }
  }

  @Test
  fun snippetMetadataCarriesLanguageAndFileMetadataDoesNot() {
    val items = AgentPromptEditorContextSupport.buildContextItems(snapshot(symbolName = null))

    val snippetItem = items.first { it.rendererId == AgentPromptContextRendererIds.SNIPPET }
    assertThat(snippetItem.payload.objOrNull()!!.string("language")).isEqualTo("kotlin")

    val fileItem = items.first { it.rendererId == AgentPromptContextRendererIds.FILE }
    assertThat(fileItem.payload.objOrNull()!!.string("language")).isNull()
  }

  @Test
  fun buildContextItemsOrdersFileSymbolThenSnippet() {
    val items = AgentPromptEditorContextSupport.buildContextItems(snapshot(symbolName = "main"))

    assertThat(items.map { it.rendererId }).containsExactly(
      AgentPromptContextRendererIds.FILE,
      AgentPromptContextRendererIds.SYMBOL,
      AgentPromptContextRendererIds.SNIPPET,
    )
  }

  @Test
  fun composeInitialMessageRendersFileSymbolThenSnippet() {
    val message = AgentPromptContextEnvelopeFormatter.composeInitialMessage(
      AgentPromptInitialMessageRequest(
        prompt = "Review context",
        contextItems = AgentPromptEditorContextSupport.buildContextItems(snapshot(symbolName = "main")),
      )
    )

    val fileIndex = message.indexOf("file: /tmp/Sample.kt")
    val symbolIndex = message.indexOf("symbol: main")
    val snippetIndex = message.indexOf("snippet:")

    assertThat(fileIndex).isGreaterThanOrEqualTo(0)
    assertThat(symbolIndex).isGreaterThan(fileIndex)
    assertThat(snippetIndex).isGreaterThan(symbolIndex)
  }

  private fun snapshot(symbolName: String?): AgentEditorContextSnapshot {
    return AgentEditorContextSnapshot(
      filePath = "/tmp/Sample.kt",
      language = "kotlin",
      snippet = AgentPromptSnippet(
        text = "val answer = 42",
        startLine = 1,
        endLine = 1,
        fromSelection = false,
        originalChars = 15,
        includedChars = 15,
        truncated = false,
        truncationReason = AgentPromptContextTruncationReason.NONE,
      ),
      symbolName = symbolName,
    )
  }

  private fun invocationData(project: com.intellij.openapi.project.Project, dataContext: DataContext): AgentPromptInvocationData {
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent",
      actionPlace = "EditorPopup",
      invokedAtMs = 0L,
      attributes = mapOf(
        AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY to dataContext,
      ),
    )
  }
}
