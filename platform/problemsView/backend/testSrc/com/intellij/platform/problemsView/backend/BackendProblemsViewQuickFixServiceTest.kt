// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.codeInspection.CustomSuppressableInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPlainText
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.enableInspectionTool
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
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
      .psiFileFixture("testFile.java", "class TestMe {}\n")
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
      service.discardQuickFixModel(second.quickFixModelId)
    }
  }

  @Test
  @Timeout(30)
  fun `discard removes loaded quick fixes`(): Unit = timeoutRunBlocking {
    withEditor {
      val highlighterId = addQuickFix()
      val service = BackendProblemsViewQuickFixService.getInstance(project)
      val quickFixModel = requireNotNull(service.loadQuickFixes(testFile.virtualFile.rpcId(), highlighterId))

      service.discardQuickFixModel(quickFixModel.quickFixModelId)

      assertFalse(service.hasLoadedQuickFixes())
    }
  }

  @Test
  @Timeout(30)
  fun `discard does not remove a newer quick fix model`(): Unit = timeoutRunBlocking {
    withEditor {
      val highlighterId = addQuickFix()
      val service = BackendProblemsViewQuickFixService.getInstance(project)
      val first = requireNotNull(service.loadQuickFixes(testFile.virtualFile.rpcId(), highlighterId))
      val second = requireNotNull(service.loadQuickFixes(testFile.virtualFile.rpcId(), highlighterId))

      service.discardQuickFixModel(first.quickFixModelId)
      assertTrue(service.hasLoadedQuickFixes())

      service.discardQuickFixModel(second.quickFixModelId)
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

      service.executeQuickFix(quickFixes.quickFixModelId, intentionId)
      assertFalse(service.hasLoadedQuickFixes())
    }
  }

  @Test
  @Timeout(30)
  fun `inspection suppression uses the problem element`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    withEditor {
      class TestSuppress : SuppressIntentionAction() {
        override fun getText(): String = familyName

        override fun getFamilyName(): String = "Suppress test class problem"

        override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean = true

        override fun invoke(project: Project, editor: Editor?, element: PsiElement) = Unit
      }

      class TestClassInspection : LocalInspectionTool(), CustomSuppressableInspectionTool {
        override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
          return object : PsiElementVisitor() {
            override fun visitPlainText(content: PsiPlainText) {
              holder.registerProblem(content, "Test class problem")
            }
          }
        }

        override fun getSuppressActions(element: PsiElement?): Array<SuppressIntentionAction> {
          return if (element is PsiFile) emptyArray() else arrayOf(TestSuppress())
        }

        override fun isSuppressedFor(element: PsiElement): Boolean = false
      }

      val inspection = TestClassInspection()
      enableInspectionTool(project, inspection, disposable)
      val key = requireNotNull(HighlightDisplayKey.find(inspection.shortName))
      val info = addQuickFixInfo(key)
      val highlighterId = getHighlighterId(requireNotNull(info.highlighter))
      val service = BackendProblemsViewQuickFixService.getInstance(project)

      requireNotNull(service.loadQuickFixes(testFile.virtualFile.rpcId(), highlighterId))

      assertTrue(info.findRegisteredQuickFix { descriptor, _ ->
        val intentionActions = descriptor.getOptions(testFile.findElementAt(0)!!, null)
        intentionActions.any { it is TestSuppress }
      } == true)
    }
  }

  private suspend fun <T> withEditor(action: suspend () -> T): T {
    val editor = withContext(Dispatchers.EDT) {
      EditorFactory.getInstance().createEditor(testFile.viewProvider.document, project)
    }
    try {
      return action()
    }
    finally {
      withContext(Dispatchers.EDT) { EditorFactory.getInstance().releaseEditor(editor) }
    }
  }

  private suspend fun addQuickFix(key: HighlightDisplayKey? = null): Long {
    val info = addQuickFixInfo(key)
    return getHighlighterId(requireNotNull(info.highlighter))
  }

  private suspend fun addQuickFixInfo(key: HighlightDisplayKey? = null): HighlightInfo {
    return withContext(Dispatchers.EDT) {
      val document = testFile.viewProvider.document
      val info = requireNotNull(
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(0, 0)
          .registerFix(EmptyIntentionAction("test"), null, null, null, key)
          .create()
      )
      UpdateHighlightersUtil.setHighlightersToEditor(project, document, 0, 0, listOf(info), null, 1)
      info
    }
  }

  private fun getHighlighterId(highlighter: Any): Long {
    return (highlighter.javaClass.getMethod("getId").invoke(highlighter) as Number).toLong()
  }
}
