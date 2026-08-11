// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JPanel

@TestApplication
class ProblemsViewToggleCurrentFileProblemsHiddenPanelTest {
  private val projectFixture = projectFixture()
  private val project by projectFixture

  @TestDisposable
  private lateinit var testRootDisposable: Disposable

  private var toolWindow: ToolWindow? = null

  @BeforeEach
  fun setUp() {
    runInEdtAndWait {
      val problemsToolWindow = object : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
        override fun getId(): String = ToolWindowId.PROBLEMS_VIEW
      }
      toolWindow = problemsToolWindow

      val toolWindowManager = object : ToolWindowHeadlessManagerImpl(project) {
        override fun getToolWindow(id: String?): ToolWindow? = if (id == ToolWindowId.PROBLEMS_VIEW) problemsToolWindow else null
      }

      project.replaceService(ToolWindowManager::class.java, toolWindowManager, testRootDisposable)
    }
  }

  @AfterEach
  fun tearDown() {
    runInEdtAndWait {
      toolWindow?.let { Disposer.dispose(it.contentManager as Disposable) }
      toolWindow = null
    }
  }

  @Test
  fun testToggleCurrentFileProblemsUpdatesHiddenHighlightingPanel() {
    runInEdtAndWait {
      val toolWindow = requireNotNull(toolWindow)
      val panel = TestCurrentFileProblemsTab()
      val content = toolWindow.contentManager.factory.createContent(panel, "Current File", false)
      toolWindow.contentManager.addContent(content)

      ProblemsViewToolWindowUtils.selectContent(toolWindow.contentManager, HighlightingPanel.ID)
      assertFalse(panel.isShowing, "Precondition: current-file tab must not be showing")

      val fileA = LightVirtualFile("GoodFile.java")
      val docA = EditorFactory.getInstance().createDocument("class GoodFile {}")
      val fileB = LightVirtualFile("BadFile.java")
      val docB = EditorFactory.getInstance().createDocument("class BadFile { int x = \"Incompatible types\"; }")

      panel.setCurrentFile(fileA, docA)
      assertEquals(fileA, panel.getCurrentFile())

      ProblemsView.toggleCurrentFileProblems(project, fileB, docB)

      assertEquals(fileB, panel.getCurrentFile())
    }
  }

  private class TestCurrentFileProblemsTab : JPanel(), ProblemsViewTab, CurrentFileProblemsTab {
    private var currentFile: VirtualFile? = null

    override fun getName(count: Int): String = "Current File"

    override fun getTabId(): String = HighlightingPanel.ID

    override fun setCurrentFile(virtualFile: VirtualFile?, document: Document?) {
      currentFile = virtualFile
    }

    override fun getCurrentFile(): VirtualFile? = currentFile
  }
}
