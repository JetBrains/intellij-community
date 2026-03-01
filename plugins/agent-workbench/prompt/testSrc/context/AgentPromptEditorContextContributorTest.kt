// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextMetadataKeys
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReasons
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
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

        assertThat(result.map { it.kindId }).contains(AgentPromptContextKinds.SNIPPET, AgentPromptContextKinds.FILE)
        val snippetItem = result.first { it.kindId == AgentPromptContextKinds.SNIPPET }
        assertThat(snippetItem.metadata).containsKeys("startLine", "endLine", "selection")
        assertThat(snippetItem.metadata[AgentPromptContextMetadataKeys.SOURCE]).isEqualTo("editor")
        assertThat(snippetItem.metadata).containsKeys(
          AgentPromptContextMetadataKeys.ORIGINAL_CHARS,
          AgentPromptContextMetadataKeys.INCLUDED_CHARS,
          AgentPromptContextMetadataKeys.TRUNCATED,
          AgentPromptContextMetadataKeys.TRUNCATION_REASON,
        )
        val fileItem = result.first { it.kindId == AgentPromptContextKinds.FILE }
        assertThat(fileItem.content).contains("Sample.kt")
        assertThat(fileItem.metadata[AgentPromptContextMetadataKeys.SOURCE]).isEqualTo("editor")
      }
      finally {
        editorFactory.releaseEditor(editor)
      }
    }
  }

  @Test
  fun snippetMetadataCarriesLanguageAndFileMetadataDoesNot() {
    val snapshot = AgentEditorContextSnapshot(
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
        truncationReason = AgentPromptContextTruncationReasons.NONE,
      ),
      symbolName = null,
    )

    val items = AgentPromptEditorContextSupport.buildContextItems(snapshot)

    val snippetItem = items.first { it.kindId == AgentPromptContextKinds.SNIPPET }
    assertThat(snippetItem.metadata[AgentPromptContextMetadataKeys.LANGUAGE]).isEqualTo("kotlin")

    val fileItem = items.first { it.kindId == AgentPromptContextKinds.FILE }
    assertThat(fileItem.metadata).doesNotContainKey(AgentPromptContextMetadataKeys.LANGUAGE)
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
