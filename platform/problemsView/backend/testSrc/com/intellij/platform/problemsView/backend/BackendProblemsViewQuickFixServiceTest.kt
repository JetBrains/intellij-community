// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.UUID

@TestApplication
internal class BackendProblemsViewQuickFixServiceTest {
  companion object {
    private val projectFixture = projectFixture()
    private val testFileFixture = projectFixture
      .moduleFixture("testModule")
      .sourceRootFixture()
      .psiFileFixture("testFile.java", "\n")
  }

  private val project by projectFixture
  private val testFile by testFileFixture

  @Test
  @Timeout(30)
  fun `each load creates a new quick fix model`(): Unit = timeoutRunBlocking {
    withEditor {
      val highlighterId = addQuickFix()
      val service = BackendProblemsViewQuickFixService.getInstance(project)

      val first = service.loadQuickFixes(testFile.virtualFile.rpcId(), highlighterId)
      val second = service.loadQuickFixes(testFile.virtualFile.rpcId(), highlighterId)

      assertNotNull(first)
      assertNotNull(second)
      assertNotEquals(first!!.quickFixModelId, second!!.quickFixModelId)
      assertNotEquals(first.quickFixes.single().intentionId, second.quickFixes.single().intentionId)
      UUID.fromString(first.quickFixes.single().intentionId)
      assertTrue(service.hasLoadedQuickFixes())
      service.discardQuickFixes()
    }
  }

  @Test
  @Timeout(30)
  fun `discard removes loaded quick fixes`(): Unit = timeoutRunBlocking {
    withEditor {
      val highlighterId = addQuickFix()
      val service = BackendProblemsViewQuickFixService.getInstance(project)
      assertNotNull(service.loadQuickFixes(testFile.virtualFile.rpcId(), highlighterId))

      service.discardQuickFixes()

      assertFalse(service.hasLoadedQuickFixes())
    }
  }

  @Test
  @Timeout(30)
  fun `only a matching quick fix can be executed`(): Unit = timeoutRunBlocking {
    withEditor {
      val highlighterId = addQuickFix()
      val service = BackendProblemsViewQuickFixService.getInstance(project)
      val quickFixes = requireNotNull(service.loadQuickFixes(testFile.virtualFile.rpcId(), highlighterId))
      val intentionId = quickFixes.quickFixes.single().intentionId

      service.executeQuickFix("another model", intentionId)
      service.executeQuickFix(quickFixes.quickFixModelId, "another intention")
      assertTrue(service.hasLoadedQuickFixes())

      val highlighter = findHighlighter(highlighterId)
      withContext(Dispatchers.EDT) { highlighter.dispose() }
      service.executeQuickFix(quickFixes.quickFixModelId, intentionId)
      assertFalse(service.hasLoadedQuickFixes())
    }
  }

  private suspend fun <T> withEditor(action: suspend () -> T): T {
    val document = readAction { testFile.viewProvider.document }
    val editor = withContext(Dispatchers.EDT) { EditorFactory.getInstance().createEditor(document, project) }
    try {
      return action()
    }
    finally {
      withContext(Dispatchers.EDT) { EditorFactory.getInstance().releaseEditor(editor) }
    }
  }

  private suspend fun addQuickFix(): Long {
    val document = readAction { testFile.viewProvider.document }
    val info = requireNotNull(readAction {
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(0, 0)
        .registerFix(EmptyIntentionAction("test"), null, null, null, null)
        .create()
    })
    withContext(Dispatchers.EDT) {
      UpdateHighlightersUtil.setHighlightersToEditor(project, document, 0, 0, listOf(info), null, 1)
    }
    return getHighlighterId(requireNotNull(info.highlighter))
  }

  private suspend fun findHighlighter(highlighterId: Long): RangeHighlighter {
    return readAction {
      val document = testFile.viewProvider.document
      DocumentMarkupModel.forDocument(document, project, false).allHighlighters.single { getHighlighterId(it) == highlighterId }
    }
  }

  private fun getHighlighterId(highlighter: Any): Long {
    return (highlighter.javaClass.getMethod("getId").invoke(highlighter) as Number).toLong()
  }
}
