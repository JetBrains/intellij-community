// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabActionProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.registryKeyFixture
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests [ToolWindowEditorTabManager]
 */
@Suppress("DEPRECATION") // Disposer.isDisposed is the clearest check that a content survived a tab close.
@TestApplication
class ToolWindowEditorTabManagerTest {
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
  private val controller: ToolWindowEditorTabTransferController
    get() = ToolWindowEditorTabTransferController.getInstance(project)

  private val toolWindowId = "TestToolWindow"

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    // Force the lazy fixtures to initialize (enables the registry key, installs the editor manager).
    registryFixture.get()
    manager.closeAllFiles()
    // Real tool window cells initialize tab-label actions. The Code With Me provider hard-casts the project
    // tool window manager, which is unrelated to the disposal logic under test here.
    ExtensionTestUtil.maskExtensions(ContentTabActionProvider.EP_NAME, emptyList(), disposable)
    registerFakeToolWindowEditorTabSupport(
      toolWindowId,
      FakeToolWindowEditorTabSupport(flowOf(ToolWindowEditorTabPresentation("Tab"))),
      disposable,
    )
  }

  /**
   * A tool window backed by a real [ContentManager]. The headless [ToolWindowHeadlessManagerImpl]
   * does not carry the id into its mock tool window, so the id is overridden explicitly.
   */
  private fun createToolWindow(id: String = toolWindowId): ToolWindow {
    val contentManager = ContentFactory.getInstance().createContentManager(false, project)
    Disposer.register(disposable, contentManager)
    return object : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
      override fun getId(): String = id
      override fun getContentManager(): ContentManager = contentManager
    }
  }

  private fun addContent(toolWindow: ToolWindow, displayName: String = "tab"): Content {
    val content = createTabContent(displayName = displayName)
    toolWindow.contentManager.addContent(content)
    return content
  }

  private fun openTabFiles(): List<ToolWindowEditorTabFile> =
    manager.openFiles.filterIsInstance<ToolWindowEditorTabFile>()

  private fun openTabFile(): ToolWindowEditorTabFile = openTabFiles().single()

  // Tests that [ToolWindowEditorTabManager]

  @Test
  fun `releasing the attached content closes the editor tab`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow()
      val content = addContent(toolWindow)
      controller.moveContentToEditor(toolWindow, content)
      val tabFile = openTabFile()
      assertThat(manager.isFileOpen(tabFile)).isTrue()

      content.release()

      assertThat(manager.isFileOpen(tabFile)).isFalse()
      assertThat(openTabFiles()).isEmpty()
      // The session and the file must not outlive the content that backed them.
      assertThat(tabFile.session(project)).isNull()
      assertThat(tabFile.isValid).isFalse()
    }

  @Test
  fun `disposing the content closes the tab file once`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val content = createTabContent()
      val tabFile = createTabFile(project = project, toolWindowId = toolWindowId, content = content)
      // The hook resolves FileEditorManager when it runs, so install the recorder before the release.
      // The recorder also keeps the editor from closing, which isolates the hook from the close cycle it starts.
      val recordingManager = RecordingFileEditorManager(project)
      project.replaceService(FileEditorManager::class.java, recordingManager, disposable)

      content.release()

      assertThat(recordingManager.closeRequests).containsExactly(tabFile)
    }

  @Test
  fun `moving the content back to the tool window keeps it alive`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow()
      val content = addContent(toolWindow)
      controller.moveContentToEditor(toolWindow, content)
      val tabFile = openTabFile()

      controller.moveContentToToolWindow(toolWindow, tabFile)

      // The move back transfers ownership without a release, so the content must survive the tab close.
      assertThat(Disposer.isDisposed(content)).isFalse()
      assertThat(toolWindow.contentManager.contents.toList()).contains(content)

      // The hook stays on the content. A later disposal must close nothing but the already invalid file.
      toolWindow.contentManager.removeContent(content, true)

      assertThat(Disposer.isDisposed(content)).isTrue()
      assertThat(openTabFiles()).isEmpty()
    }
}
