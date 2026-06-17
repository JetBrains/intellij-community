// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.UUID
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchOpenScratchAsPromptActionTest {
  @Test
  fun editorPopupShowsForNonEmptyScratchMarkdownEditor() {
    runInEdtAndWait {
      withScratchEditor("# Prompt") { editor, file ->
        val dataContext = editorDataContext(editor)

        assertThat(resolveScratchMarkdownPromptText(dataContext)).isEqualTo("# Prompt")
        assertThat(isVisible(dataContext)).isTrue()
        assertThat(file.isValid).isTrue()
      }
    }
  }

  @Test
  fun editorPopupHidesForNonScratchMarkdownEditor() {
    runInEdtAndWait {
      withStandaloneEditor { editor ->
        val dataContext = editorDataContext(editor, LightVirtualFile("prompt.md", MarkdownFileType.INSTANCE, editor.document.text))

        assertThat(resolveScratchMarkdownPromptText(dataContext)).isNull()
        assertThat(isVisible(dataContext)).isFalse()
      }
    }
  }

  @Test
  fun editorPopupHidesForScratchTextEditor() {
    runInEdtAndWait {
      withScratchEditor("prompt", fileName = uniqueScratchName("txt"), language = null) { editor, file ->
        val dataContext = editorDataContext(editor, file)

        assertThat(resolveScratchMarkdownPromptText(dataContext)).isNull()
        assertThat(isVisible(dataContext)).isFalse()
      }
    }
  }

  @Test
  fun editorPopupHidesForBlankScratchMarkdownEditor() {
    runInEdtAndWait {
      withScratchEditor(" \n\t") { editor, file ->
        val dataContext = editorDataContext(editor, file)

        assertThat(resolveScratchMarkdownPromptText(dataContext)).isNull()
        assertThat(isVisible(dataContext)).isFalse()
      }
    }
  }

  @Test
  fun actionPerformedPassesScratchMarkdownTextToPromptOpener() {
    var openedText: String? = null
    var openedEvent: AnActionEvent? = null
    val action = AgentWorkbenchOpenScratchAsPromptAction(openPrompt = { event, promptText ->
      openedEvent = event
      openedText = promptText
    })

    runInEdtAndWait {
      withScratchEditor("\n# Prompt\n\n") { editor, file ->
        val event = event(action, editorDataContext(editor, file))

        action.update(event)
        action.actionPerformed(event)

        assertThat(event.presentation.isEnabledAndVisible).isTrue()
        assertThat(openedEvent).isSameAs(event)
        assertThat(openedText).isEqualTo("\n# Prompt\n\n")
      }
    }
  }

  @Test
  fun editorPopupUsesResolvedProviderIconForScratchMarkdownEditor() {
    val icon = EmptyIcon.ICON_16
    val action = AgentWorkbenchOpenScratchAsPromptAction(
      openPrompt = { _, _ -> },
      resolveIcon = { icon },
    )

    runInEdtAndWait {
      withScratchEditor("# Prompt") { editor, file ->
        val event = event(action, editorDataContext(editor, file))

        action.update(event)

        assertThat(event.presentation.isEnabledAndVisible).isTrue()
        assertThat(event.presentation.icon).isSameAs(icon)
      }
    }
  }

  @Test
  fun floatingToolbarProviderUsesScratchPromptActionGroup() {
    val provider = AgentWorkbenchScratchPromptFloatingToolbarProvider()

    runInEdtAndWait {
      withScratchEditor("# Prompt") { editor, file ->
        val dataContext = editorDataContext(editor, file)

        assertThat(runBlocking { provider.isApplicableAsync(dataContext) }).isTrue()
        assertThat(provider.actionGroup.childActionIds()).containsExactly(AGENT_WORKBENCH_OPEN_SCRATCH_AS_PROMPT_ACTION_ID)
      }
    }
  }

  @Test
  fun actionAndFloatingToolbarProviderAreRegistered() {
    val actionManager = ActionManager.getInstance()
    val registeredAction = actionManager.getAction(AGENT_WORKBENCH_OPEN_SCRATCH_AS_PROMPT_ACTION_ID)

    assertThat(registeredAction).isInstanceOf(AgentWorkbenchOpenScratchAsPromptAction::class.java)
    assertThat(actionManager.childActionIds("EditorPopupMenu")).contains(AGENT_WORKBENCH_OPEN_SCRATCH_AS_PROMPT_ACTION_ID)
    assertThat(actionManager.childActionIds(AGENT_WORKBENCH_SCRATCH_PROMPT_CONTEXT_BAR_GROUP_ID))
      .containsExactly(AGENT_WORKBENCH_OPEN_SCRATCH_AS_PROMPT_ACTION_ID)
    assertThat(FloatingToolbarProvider.EP_NAME.extensionList)
      .anySatisfy { provider -> assertThat(provider).isInstanceOf(AgentWorkbenchScratchPromptFloatingToolbarProvider::class.java) }
  }

  private fun isVisible(dataContext: DataContext): Boolean {
    val action = AgentWorkbenchOpenScratchAsPromptAction(openPrompt = { _, _ -> })
    val event = event(action, dataContext)
    action.update(event)
    return event.presentation.isEnabledAndVisible
  }

  private fun event(action: AgentWorkbenchOpenScratchAsPromptAction, dataContext: DataContext): AnActionEvent {
    return AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.EDITOR_POPUP, ActionUiKind.POPUP, null)
  }

  private fun editorDataContext(editor: Editor, file: VirtualFile? = null): DataContext {
    return SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.EDITOR, editor)
      .apply {
        if (file != null) {
          add(CommonDataKeys.VIRTUAL_FILE, file)
        }
      }
      .build()
  }

  private fun withScratchEditor(
    text: String,
    fileName: String = uniqueScratchName("md"),
    language: Language? = MarkdownLanguage.INSTANCE,
    block: (Editor, VirtualFile) -> Unit,
  ) {
    val file = runWriteAction {
      checkNotNull(ScratchRootType.getInstance().createScratchFile(project, fileName, language, text))
    }
    try {
      withFileEditor(file) { editor -> block(editor, file) }
    }
    finally {
      runWriteAction {
        if (file.isValid) {
          file.delete(this)
        }
      }
    }
  }

  private fun withFileEditor(file: VirtualFile, block: (Editor) -> Unit) {
    val document = checkNotNull(FileDocumentManager.getInstance().getDocument(file))
    val editorFactory = EditorFactory.getInstance()
    val editor = editorFactory.createEditor(document, project)
    try {
      block(editor)
    }
    finally {
      editorFactory.releaseEditor(editor)
    }
  }

  private fun withStandaloneEditor(block: (Editor) -> Unit) {
    val editorFactory = EditorFactory.getInstance()
    val editor = editorFactory.createEditor(editorFactory.createDocument("# Prompt"), project)
    try {
      block(editor)
    }
    finally {
      editorFactory.releaseEditor(editor)
    }
  }

  private fun ActionManager.childActionIds(groupId: String): List<String> {
    val group = getAction(groupId) as? ActionGroup
    assertThat(group).withFailMessage("Action group '%s' is not registered", groupId).isNotNull
    return checkNotNull(group).childActionIds()
  }

  private fun ActionGroup.childActionIds(): List<String> {
    val actionManager = ActionManager.getInstance()
    return getChildren(TestActionEvent.createTestEvent()).mapNotNull { action -> actionManager.getId(action) }
  }

  private val project: Project
    get() = ProjectManager.getInstance().defaultProject
}

private fun uniqueScratchName(extension: String): String = "agent-prompt-${UUID.randomUUID()}.$extension"
