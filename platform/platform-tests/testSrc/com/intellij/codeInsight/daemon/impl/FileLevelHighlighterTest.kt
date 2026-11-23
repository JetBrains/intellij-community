// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.UsePMarkerImplementation
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
@UsePMarkerImplementation
@Suppress("SuspiciousPackagePrivateAccess")
internal class FileLevelHighlighterTest {
  private val projectFixture: TestFixture<Project> = projectFixture()
  private val managerFixture: TestFixture<FileEditorManagerImpl> = projectFixture.fileEditorManagerFixture()

  @ParameterizedTest
  @EnumSource(Cleanup::class)
  fun `cleanup preserves a highlighter transferred to another result`(cleanup: Cleanup): Unit = timeoutRunBlocking {
    checkCleanup(cleanup)
  }

  @ParameterizedTest
  @EnumSource(Cleanup::class)
  @UsePMarkerImplementation(false)
  fun `legacy cleanup preserves a highlighter transferred to another result`(cleanup: Cleanup): Unit = timeoutRunBlocking {
    checkCleanup(cleanup)
  }

  private suspend fun checkCleanup(cleanup: Cleanup) {
    withContext(Dispatchers.EDT) {
      val project = projectFixture.get()
      val manager = managerFixture.get()
      val virtualFile = LightVirtualFile("test.txt", "text")
      val editor = manager.openFile(virtualFile, true).filterIsInstance<TextEditor>().single().editor
      val psiFile = checkNotNull(PsiDocumentManager.getInstance(project).getPsiFile(editor.document))
      val markup = DocumentMarkupModel.forDocument(editor.document, project, true) as MarkupModelEx
      val analyzer = DaemonCodeAnalyzerEx.getInstanceEx(project) as DaemonCodeAnalyzerImpl
      val updater = HighlightInfoUpdater.getInstance(project) as HighlightInfoUpdaterImpl
      val group = HighlightInfoUpdaterImpl.MANAGED_HIGHLIGHT_INFO_GROUP
      val oldInfo = info()
      val highlighter = updater.createOrReuseFakeFileLevelHighlighter(group, oldInfo, null, markup, project, null)
      analyzer.addFileLevelHighlight(group, oldInfo, psiFile, highlighter, null)
      val newInfo = info()
      assertSame(highlighter, updater.createOrReuseFakeFileLevelHighlighter(group, newInfo, highlighter, markup, project, null))

      when (cleanup) {
        Cleanup.REMOVE -> analyzer.removeFileLevelHighlight(psiFile, oldInfo)
        Cleanup.CLEAN -> analyzer.cleanFileLevelHighlights(group, psiFile)
        Cleanup.REPLACE -> analyzer.replaceFileLevelHighlight(oldInfo, info("Other warning"), psiFile, null, null)
        Cleanup.REMOVE_AFTER_REPLACEMENT -> {
          oldInfo.invalidate()
          analyzer.replaceFileLevelHighlight(oldInfo, newInfo, psiFile, highlighter, null)
          analyzer.removeFileLevelHighlight(psiFile, oldInfo)
          assertEquals(listOf(newInfo), analyzer.getFileLevelHighlights(project, psiFile))
        }
      }

      assertTrue(highlighter.isValid)
      assertSame(newInfo, HighlightInfo.fromRangeHighlighter(highlighter))
      oldInfo.invalidate()
      analyzer.addFileLevelHighlight(group, newInfo, psiFile, highlighter, null)
      analyzer.removeFileLevelHighlight(psiFile, newInfo)
      assertFalse(highlighter.isValid)
      assertTrue(analyzer.getFileLevelHighlights(project, psiFile).none { it === newInfo })
    }
  }

  private fun info(description: String = "Warning"): HighlightInfo =
    HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
      .range(0, 4)
      .description(description)
      .fileLevelAnnotation()
      .createUnconditionally()

  enum class Cleanup {
    REMOVE,
    CLEAN,
    REPLACE,
    REMOVE_AFTER_REPLACEMENT,
  }
}
