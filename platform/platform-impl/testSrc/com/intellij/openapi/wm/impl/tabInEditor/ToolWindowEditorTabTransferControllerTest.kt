// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.registryKeyFixture
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.content.Content
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabActionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JSplitPane
import javax.swing.SwingConstants

@TestApplication
class ToolWindowEditorTabTransferControllerTest {
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
    registerSupport(toolWindowId)
  }

  private fun registerSupport(
    id: String,
    canBeMovedToEditorAction: ((Content) -> Boolean)? = null,
  ): FakeToolWindowEditorTabSupport {
    val support = FakeToolWindowEditorTabSupport(
      presentationFlow = flowOf(ToolWindowEditorTabPresentation("Tab")),
      canBeMovedToEditorAction = canBeMovedToEditorAction,
    )
    registerFakeToolWindowEditorTabSupport(id, support, disposable)
    return support
  }

  private fun createToolWindow(id: String): FakeToolWindow = FakeToolWindow(project, id, disposable)

  private fun createRegisteredToolWindow(): ToolWindowImpl = registerLocalToolWindow(project, toolWindowId, disposable)

  private fun createDetachedTabFile(content: Content = createTabContent()): ToolWindowEditorTabFile =
    createTabFile(project = project, toolWindowId = toolWindowId, content = content)

  /**
   * Two editor windows that both show [plainFile], as a user gets them from the "Split Right" action.
   */
  private class SplitEditor(val plainFile: LightVirtualFile, val firstWindow: EditorWindow, val secondWindow: EditorWindow)

  private fun splitEditor(): SplitEditor {
    val plainFile = LightVirtualFile("plain.txt")
    manager.openFile(plainFile, true)
    val firstWindow = requireNotNull(manager.currentWindow)
    val secondWindow = requireNotNull(firstWindow.split(JSplitPane.HORIZONTAL_SPLIT, true, plainFile, true))
    return SplitEditor(plainFile, firstWindow, secondWindow)
  }

  @Test
  fun `move content to editor opens a tool window editor tab`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow(toolWindowId)
      val content = toolWindow.addTabContent()

      assertThat(controller.canMoveContentToEditor(toolWindow, content)).isTrue()
      controller.moveContentToEditor(toolWindow, content)

      val tabFile = manager.openTabFile()
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

      assertThat(manager.openTabFile().attachedContent(project)).isSameAs(movingContent)
      assertThat(rootDecorator.mode).isEqualTo(InternalDecoratorImpl.Mode.SINGLE)
    }

  @Test
  fun `move content to editor keeps the source decorator split while it holds other content`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createRegisteredToolWindow()
      val rootDecorator = toolWindow.getOrCreateDecoratorComponent()
      val movingContent = createTabContent(displayName = "moving")
      rootDecorator.splitWithContent(movingContent, SwingConstants.RIGHT, -1)
      val sourceDecorator = findDecorator(movingContent)
      val stayingContent = createTabContent(displayName = "staying")
      sourceDecorator.contentManager.addContent(stayingContent)

      controller.moveContentToEditor(toolWindow, movingContent, sourceDecorator = sourceDecorator)

      assertThat(manager.openTabFile().attachedContent(project)).isSameAs(movingContent)
      assertThat(rootDecorator.mode.isSplit).isTrue()
      assertThat(sourceDecorator.contentManager.contents.toList()).containsExactly(stayingContent)
    }

  @Test
  fun `move content to editor opens the tab in the given editor window`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow(toolWindowId)
      val content = toolWindow.addTabContent()
      val editor = splitEditor()
      manager.currentWindow = editor.secondWindow

      // Drag-and-drop passes the window under the cursor, which is not necessarily the current one.
      controller.moveContentToEditor(toolWindow, content, window = editor.firstWindow)

      val tabFile = manager.openTabFile()
      assertThat(editor.firstWindow.getComposite(tabFile)).isNotNull()
      assertThat(editor.secondWindow.getComposite(tabFile)).isNull()
    }

  @Test
  fun `move content to editor returns the content to the tool window when the editor tab does not open`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow(toolWindowId)
      val content = toolWindow.addTabContent()
      val closedFiles = mutableSetOf<VirtualFile>()
      project.messageBus.connect(disposable).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
          closedFiles += file
        }
      })
      // Without a file editor provider the composite opens empty, and the editor manager closes it at once.
      ExtensionTestUtil.maskExtensions(FileEditorProvider.EP_FILE_EDITOR_PROVIDER, emptyList(), disposable)

      controller.moveContentToEditor(toolWindow, content)

      assertThat(manager.openTabFiles()).isEmpty()
      // The content is not lost: it is back in the tool window, selected, alive, and no longer temporarily removed.
      assertThat(toolWindow.contentManager.contents.toList()).containsExactly(content)
      assertThat(toolWindow.contentManager.selectedContent).isSameAs(content)
      assertThat(Disposer.isDisposed(content)).isFalse()
      assertThat(content.getUserData(Content.TEMPORARY_REMOVED_KEY)).isNull()
      // The file created for the failed open is closed and cannot be reused.
      val tabFile = closedFiles.filterIsInstance<ToolWindowEditorTabFile>().single()
      assertThat(tabFile.isValid).isFalse()
      assertThat(tabFile.session(project)).isNull()
    }

  @Test
  fun `move content back to tool window restores it and invalidates the file`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow(toolWindowId)
      val content = toolWindow.addTabContent()
      controller.moveContentToEditor(toolWindow, content)
      val tabFile = manager.openTabFile()

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
      val tabFile = manager.openTabFile()

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
  fun `move content back closes the tab in the window that holds it when another window is current`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow(toolWindowId)
      val content = toolWindow.addTabContent()
      val editor = splitEditor()
      controller.moveContentToEditor(toolWindow, content, window = editor.firstWindow)
      val tabFile = manager.openTabFile()
      manager.currentWindow = editor.secondWindow

      controller.moveContentToToolWindow(toolWindow, tabFile)

      assertThat(manager.isFileOpen(tabFile)).isFalse()
      assertThat(tabFile.isValid).isFalse()
      assertThat(toolWindow.contentManager.contents.toList()).contains(content)
      // The current window and its file stay as they are.
      assertThat(editor.secondWindow.isDisposed).isFalse()
      assertThat(editor.secondWindow.getComposite(editor.plainFile)).isNotNull()
    }

  @Test
  fun `move content back closes a tab that has no content`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      // A tab whose restore failed: the file exists, but it has neither a session nor a stored state.
      val tabFile = ToolWindowEditorTabFile(toolWindowId = toolWindowId, persistentPath = null)
      val recordingManager = RecordingFileEditorManager(project)
      project.replaceService(FileEditorManager::class.java, recordingManager, disposable)
      val toolWindow = createToolWindow(toolWindowId)

      controller.moveContentToToolWindow(toolWindow, tabFile)

      assertThat(recordingManager.closeRequests).containsExactly(tabFile)
      assertThat(toolWindow.contentManager.contents).isEmpty()
      assertThat(tabFile.isValid).isFalse()
    }

  @Test
  fun `move content back falls back to generic close when the source window is missing`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      // The tab session is gone once the tab is closed, so keep a handle on the content up front.
      val content = createTabContent()
      val tabFile = createDetachedTabFile(content)
      val recordingManager = RecordingFileEditorManager(project)
      project.replaceService(FileEditorManager::class.java, recordingManager, disposable)

      val toolWindow = createToolWindow(toolWindowId)
      controller.moveContentToToolWindow(toolWindow, tabFile)

      assertThat(recordingManager.closeRequests).containsExactly(tabFile)
      assertThat(recordingManager.closeInWindowRequests).isEmpty()
      assertThat(toolWindow.contentManager.contents.toList()).containsExactly(content)
      assertThat(tabFile.isValid).isFalse()
    }

  @Test
  fun `nothing moves when the feature is disabled`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow(toolWindowId)
      val content = toolWindow.addTabContent()

      val registryValue = Registry.get(ToolWindowEditorTabSupportUtil.REGISTRY_KEY)
      registryValue.setValue(false)
      try {
        assertThat(controller.canMoveContentToEditor(toolWindow, content)).isFalse()
        controller.moveContentToEditor(toolWindow, content)

        assertThat(manager.openTabFiles()).isEmpty()
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
      val content = toolWindow.addTabContent()

      assertThat(controller.canMoveContentToEditor(toolWindow, content)).isFalse()
      controller.moveContentToEditor(toolWindow, content)

      assertThat(manager.openTabFiles()).isEmpty()
    }

  @Test
  fun `move to editor is rejected when the support does not accept the content`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val rejectingToolWindowId = "RejectingToolWindow"
      registerSupport(rejectingToolWindowId, canBeMovedToEditorAction = { false })
      val toolWindow = createToolWindow(rejectingToolWindowId)
      val content = toolWindow.addTabContent()

      assertThat(controller.canMoveContentToEditor(toolWindow, content)).isFalse()
      controller.moveContentToEditor(toolWindow, content)

      assertThat(manager.openTabFiles()).isEmpty()
      assertThat(toolWindow.contentManager.contents.toList()).contains(content)
    }

  @Test
  fun `support decides per content which tab can be moved to the editor`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val mixedToolWindowId = "MixedToolWindow"
      val toolWindow = createToolWindow(mixedToolWindowId)
      val supported = toolWindow.addTabContent(displayName ="supported")
      val unsupported = toolWindow.addTabContent(displayName ="unsupported")
      val mixedSupport = registerSupport(mixedToolWindowId, canBeMovedToEditorAction = { it === supported })

      assertThat(controller.canMoveContentToEditor(toolWindow, supported)).isTrue()
      assertThat(controller.canMoveContentToEditor(toolWindow, unsupported)).isFalse()

      controller.moveContentToEditor(toolWindow, unsupported)
      assertThat(manager.openTabFiles()).isEmpty()

      controller.moveContentToEditor(toolWindow, supported)

      assertThat(manager.openTabFile().attachedContent(project)).isSameAs(supported)
      assertThat(toolWindow.contentManager.contents.toList()).containsExactly(unsupported)
      assertThat(mixedSupport.presentationFlowRequests).containsExactly(supported)
    }

  @Test
  fun `cannot move a tab back without the feature or without support`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow(toolWindowId)
      val tabFile = createDetachedTabFile()
      val registryValue = Registry.get(ToolWindowEditorTabSupportUtil.REGISTRY_KEY)
      registryValue.setValue(false)
      try {
        assertThat(controller.canMoveContentToToolWindow(toolWindow, tabFile)).isFalse()
      }
      finally {
        registryValue.setValue(true)
      }

      val unsupportedId = "UnsupportedToolWindow"
      val unsupportedToolWindow = createToolWindow(unsupportedId)
      val unsupportedTabFile = ToolWindowEditorTabFile(toolWindowId = unsupportedId, persistentPath = null)
      assertThat(controller.canMoveContentToToolWindow(unsupportedToolWindow, unsupportedTabFile)).isFalse()
    }

  @Test
  fun `cannot move a tab back to a tool window with a different id`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow(toolWindowId)
      val content = toolWindow.addTabContent()
      controller.moveContentToEditor(toolWindow, content)
      val tabFile = manager.openTabFile()

      val otherToolWindow = createToolWindow("OtherToolWindow")
      assertThat(controller.canMoveContentToToolWindow(otherToolWindow, tabFile)).isFalse()
    }
}
