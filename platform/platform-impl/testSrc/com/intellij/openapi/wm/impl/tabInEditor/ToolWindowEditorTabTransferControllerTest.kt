// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.registryKeyFixture
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabActionProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JPanel
import javax.swing.SwingConstants

@TestApplication
class ToolWindowEditorTabTransferControllerTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInit = { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture(initDockableContentFactory = true)
  private val registryFixture = registryKeyFixture(ToolWindowEditorTabSupportUtil.REGISTRY_KEY) { setValue(true) }

  private val project: Project get() = projectFixture.get()
  private val projectScope: CoroutineScope get() = (project as ComponentManagerEx).getCoroutineScope()
  private val manager: FileEditorManagerImpl get() = fileEditorManagerFixture.get()

  private val toolWindowId = "TestToolWindow"

  private val controller: ToolWindowEditorTabTransferController
    get() = ToolWindowEditorTabTransferController.getInstance(project)

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    // Force the lazy fixtures to initialize (enables the registry key, installs the editor manager).
    registryFixture.get()
    manager.closeAllFiles()
    // Real ToolWindowImpl cells initialize tab-label actions. The Code With Me provider hard-casts
    // the project tool window manager, which is unrelated to the transfer logic under test here.
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
  private fun createToolWindow(id: String): ToolWindow {
    val contentManager = ContentFactory.getInstance().createContentManager(false, project)
    Disposer.register(disposable, contentManager)
    return object : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
      override fun getId(): String = id
      override fun getContentManager(): ContentManager = contentManager
    }
  }

  private fun addContent(toolWindow: ToolWindow): Content {
    val content = createTabContent()
    toolWindow.contentManager.addContent(content)
    return content
  }

  private fun createRegisteredToolWindow(component: JPanel = JPanel()): ToolWindowImpl {
    return registerLocalToolWindow(project, toolWindowId, disposable, component)
  }

  private fun createTabFile(content: Content = createTabContent()): ToolWindowEditorTabFile = ToolWindowEditorTabFile(
    presentationFlow = flowOf(ToolWindowEditorTabPresentation("Tab")),
    toolWindowId = toolWindowId,
    component = JPanel(),
    preferredFocusedComponent = JPanel(),
    content = content,
    project = project,
    parentCoroutineScope = projectScope,
  )

  private fun openTabFile(): ToolWindowEditorTabFile =
    manager.openFiles.filterIsInstance<ToolWindowEditorTabFile>().single()

  @Test
  fun `move content to editor opens a tool window editor tab`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow(toolWindowId)
      val content = addContent(toolWindow)

      assertThat(controller.canMoveContentToEditor(toolWindow)).isTrue()
      controller.moveContentToEditor(toolWindow, content)

      val tabFile = openTabFile()
      assertThat(manager.isFileOpen(tabFile)).isTrue()
      assertThat(tabFile.toolWindowId).isEqualTo(toolWindowId)
      assertThat(manager.getSelectedEditor(tabFile)).isInstanceOf(ToolWindowEditorTabFileEditor::class.java)
      // the content was moved out of the tool window
      assertThat(toolWindow.contentManager.contents.toList()).doesNotContain(content)
    }

  @Test
  fun `move content to editor unsplits the last source decorator cell`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createRegisteredToolWindow()
      val rootDecorator = toolWindow.getOrCreateDecoratorComponent()
      val movingContent = createTabContent(displayName = "moving")
      rootDecorator.splitWithContent(movingContent, SwingConstants.RIGHT, -1)
      val sourceDecorator = findDecorator(movingContent)

      assertThat(rootDecorator.mode.isSplit).isTrue()
      controller.moveContentToEditor(toolWindow, movingContent, sourceDecorator = sourceDecorator)

      assertThat(openTabFile().content).isSameAs(movingContent)
      assertThat(rootDecorator.mode).isEqualTo(InternalDecoratorImpl.Mode.SINGLE)
    }

  @Test
  fun `move content back to tool window restores it and invalidates the file`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow(toolWindowId)
      val content = addContent(toolWindow)
      controller.moveContentToEditor(toolWindow, content)
      val tabFile = openTabFile()

      assertThat(controller.canMoveContentToToolWindow(toolWindow, tabFile)).isTrue()
      controller.moveContentToToolWindow(toolWindow, tabFile)

      assertThat(manager.isFileOpen(tabFile)).isFalse()
      assertThat(tabFile.isValid).isFalse()
      assertThat(toolWindow.contentManager.contents.toList()).contains(content)
    }

  @Test
  fun `move content back can restore into a target decorator cell`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createRegisteredToolWindow()
      val movingContent = toolWindow.contentManager.contents.single()
      controller.moveContentToEditor(toolWindow, movingContent)
      val tabFile = openTabFile()

      val rootDecorator = toolWindow.getOrCreateDecoratorComponent()
      toolWindow.contentManager.addContent(createTabContent(displayName = "placeholder"))
      val targetContent = createTabContent(displayName = "target")
      rootDecorator.splitWithContent(targetContent, SwingConstants.RIGHT, -1)
      val targetDecorator = findDecorator(targetContent)

      controller.moveContentToToolWindow(toolWindow, tabFile, targetDecorator = targetDecorator)

      assertThat(manager.isFileOpen(tabFile)).isFalse()
      assertThat(targetDecorator.contentManager.contents.toList()).contains(movingContent)
      assertThat(movingContent.manager).isSameAs(targetDecorator.contentManager)
    }

  @Test
  fun `move content back falls back to generic close when the source window is missing`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val tabFile = createTabFile()
      val recordingManager = RecordingFileEditorManager(project)
      project.replaceService(FileEditorManager::class.java, recordingManager, disposable)

      val toolWindow = createToolWindow(toolWindowId)
      controller.moveContentToToolWindow(toolWindow, tabFile)

      assertThat(recordingManager.closeRequests).containsExactly(tabFile)
      assertThat(recordingManager.closeInWindowRequests).isEmpty()
      assertThat(toolWindow.contentManager.contents.toList()).containsExactly(tabFile.content)
      assertThat(tabFile.isValid).isFalse()
    }

  @Test
  fun `nothing moves when the feature is disabled`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow(toolWindowId)
      val content = addContent(toolWindow)

      val registryValue = Registry.get(ToolWindowEditorTabSupportUtil.REGISTRY_KEY)
      registryValue.setValue(false)
      try {
        assertThat(controller.canMoveContentToEditor(toolWindow)).isFalse()
        controller.moveContentToEditor(toolWindow, content)

        assertThat(manager.openFiles.filterIsInstance<ToolWindowEditorTabFile>()).isEmpty()
        assertThat(toolWindow.contentManager.contents.toList()).contains(content)
      }
      finally {
        registryValue.setValue(true)
      }
    }

  @Test
  fun `move to editor is a no-op without registered support`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      // A tool window with no ToolWindowEditorTabSupport registered for its id.
      val toolWindow = createToolWindow("UnsupportedToolWindow")
      val content = addContent(toolWindow)

      assertThat(controller.canMoveContentToEditor(toolWindow)).isFalse()
      controller.moveContentToEditor(toolWindow, content)

      assertThat(manager.openFiles.filterIsInstance<ToolWindowEditorTabFile>()).isEmpty()
    }

  @Test
  fun `cannot move a tab back to a tool window with a different id`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow(toolWindowId)
      val content = addContent(toolWindow)
      controller.moveContentToEditor(toolWindow, content)
      val tabFile = openTabFile()

      val otherToolWindow = createToolWindow("OtherToolWindow")
      assertThat(controller.canMoveContentToToolWindow(otherToolWindow, tabFile)).isFalse()
    }
}
