// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.ide.actions.ToolWindowTabLocation
import com.intellij.ide.actions.ToolWindowTabLocationFusUtil
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabActionProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.registryKeyFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JPanel

/**
 * Tests that [ToolWindowTabLocationFusUtil] tells a tab in the tool window from the same tab opened in the editor.
 */
@TestApplication
class ToolWindowTabLocationFusUtilTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture(initDockableContentFactory = true)
  private val registryFixture = registryKeyFixture(ToolWindowEditorTabSupportUtil.REGISTRY_KEY) { setValue(true) }

  private val project: Project get() = projectFixture.get()
  private val manager: FileEditorManagerImpl get() = fileEditorManagerFixture.get()

  private val toolWindowId = "TestToolWindow"

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    registryFixture.get()
    manager.closeAllFiles()
    // Real ToolWindowImpl cells initialize tab-label actions. The Code With Me provider hard-casts
    // the project tool window manager, which is unrelated to the detection under test here.
    ExtensionTestUtil.maskExtensions(ContentTabActionProvider.EP_NAME, emptyList(), disposable)
    val support = FakeToolWindowEditorTabSupport(presentationFlow = flowOf(ToolWindowEditorTabPresentation("Tab")))
    registerFakeToolWindowEditorTabSupport(toolWindowId, support, disposable)
  }

  @Test
  fun `a component inside the tool window decorator is in the tool window`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createRegisteredToolWindow()
      toolWindow.getOrCreateDecoratorComponent()

      val location = ToolWindowTabLocationFusUtil.fromComponentHierarchy(toolWindow.contentManager.component)

      assertThat(location).isEqualTo(ToolWindowTabLocation.TOOL_WINDOW)
    }

  @Test
  fun `the content component of a tab moved to the editor is in the editor`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createRegisteredToolWindow()
      val content = toolWindow.contentManager.contents.single()

      ToolWindowEditorTabTransferController.getInstance(project).moveContentToEditor(toolWindow, content)

      assertThat(ToolWindowTabLocationFusUtil.fromComponentHierarchy(content.component)).isEqualTo(ToolWindowTabLocation.EDITOR)
    }

  @Test
  fun `a detached component has no location`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      assertThat(ToolWindowTabLocationFusUtil.fromComponentHierarchy(JPanel())).isNull()
      assertThat(ToolWindowTabLocationFusUtil.fromComponentHierarchy(null)).isNull()
    }

  private fun createRegisteredToolWindow(): ToolWindowImpl {
    return registerLocalToolWindow(project, toolWindowId, disposable, JPanel())
  }
}
