// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

import com.intellij.codeInsight.intention.AdvertisementAction
import com.intellij.codeInsight.intention.CustomizableIntentionAction
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchAddToAgentContextActionTest {
  private val action = AgentWorkbenchAddToAgentContextAction()
  private val intention = AgentWorkbenchAddToAgentContextIntention()

  @JvmField
  @RegisterExtension
  val tempDirectory: TempDirectoryExtension = TempDirectoryExtension()

  @Test
  fun editorPopupHidesWithoutProject() {
    val dataContext = SimpleDataContext.builder().build()

    assertThat(isVisible(dataContext, ActionPlaces.EDITOR_POPUP)).isFalse()
  }

  @Test
  fun editorPopupHidesForEditorWithoutFileContext() {
    runInEdtAndWait {
      withEditor("fun main() {}") { editor ->
        val dataContext = SimpleDataContext.builder()
          .add(CommonDataKeys.PROJECT, project)
          .add(CommonDataKeys.EDITOR, editor)
          .build()

        assertThat(isVisible(dataContext, ActionPlaces.EDITOR_POPUP)).isFalse()
      }
    }
  }

  @Test
  fun editorPopupHidesForEmptyDocument() {
    runInEdtAndWait {
      withEditor("") { editor ->
        val dataContext = editorDataContext(editor, LightVirtualFile("Empty.kt", ""))

        assertThat(isVisible(dataContext, ActionPlaces.EDITOR_POPUP)).isFalse()
      }
    }
  }

  @Test
  fun editorPopupShowsForFileBackedEditorWithContent() {
    runInEdtAndWait {
      withEditor("fun main() {}") { editor ->
        val dataContext = editorDataContext(editor, LightVirtualFile("Main.kt", editor.document.text))

        assertThat(isVisible(dataContext, ActionPlaces.EDITOR_POPUP)).isTrue()
      }
    }
  }

  @Test
  fun projectViewPopupHidesWithoutSelectedLocalFiles() {
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.VIRTUAL_FILE, LightVirtualFile("Scratch.kt", "println(1)"))
      .build()

    assertThat(isVisible(dataContext, ActionPlaces.PROJECT_VIEW_POPUP)).isFalse()
  }

  @Test
  fun projectViewPopupShowsWithSelectedLocalFile() {
    val file = createPhysicalFile()
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.VIRTUAL_FILE, file)
      .build()

    assertThat(isVisible(dataContext, ActionPlaces.PROJECT_VIEW_POPUP)).isTrue()
  }

  @Test
  fun otherPopupPlacesKeepProjectBasedVisibility() {
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .build()

    assertThat(isVisible(dataContext, "Vcs.Log.ContextMenu")).isTrue()
  }

  @Test
  fun editorIntentionHidesForNonLocalPsiFile() {
    runInEdtAndWait {
      withEditor("fun main() {}") { editor ->
        val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName("Scratch.txt")
        val psiFile = PsiFileFactory.getInstance(project)
          .createFileFromText("Scratch.txt", fileType, editor.document.text)

        assertThat(intention.isAvailable(project, editor, psiFile)).isFalse()
      }
    }
  }

  @Test
  fun editorIntentionShowsForLocalPsiFileWithPlainCaret() {
    val file = createPhysicalFile()
    val psiFile = checkNotNull(runReadActionBlocking { PsiManager.getInstance(project).findFile(file) })

    runInEdtAndWait {
      withEditor("fun selected() {}") { editor ->
        assertThat(editor.caretModel.currentCaret.hasSelection()).isFalse()

        assertThat(intention.isAvailable(project, editor, psiFile)).isTrue()
      }
    }
  }

  @Test
  fun editorIntentionIsAdvertisedAndDoesNotForceLightBulb() {
    assertThat(intention).isInstanceOf(AdvertisementAction::class.java)
    assertThat(intention).isInstanceOf(CustomizableIntentionAction::class.java)
    assertThat((intention as CustomizableIntentionAction).isShowLightBulb).isFalse()
    assertThat(IntentionManagerSettings.getInstance().isShowLightBulb(intention)).isFalse()
  }

  private fun editorDataContext(editor: Editor, file: VirtualFile): DataContext {
    return SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.EDITOR, editor)
      .add(CommonDataKeys.VIRTUAL_FILE, file)
      .build()
  }

  private fun isVisible(dataContext: DataContext, place: String): Boolean {
    val event = AnActionEvent.createEvent(action, dataContext, null, place, ActionUiKind.POPUP, null)
    action.update(event)
    return event.presentation.isEnabledAndVisible
  }

  private fun withEditor(text: String, block: (Editor) -> Unit) {
    val editorFactory = EditorFactory.getInstance()
    val editor = editorFactory.createEditor(editorFactory.createDocument(text))
    try {
      block(editor)
    }
    finally {
      editorFactory.releaseEditor(editor)
    }
  }

  private fun createPhysicalFile(): VirtualFile {
    return tempDirectory.newVirtualFile("Selected.kt", "fun selected() {}".toByteArray())
  }

  private val project: Project
    get() = ProjectManager.getInstance().defaultProject
}
