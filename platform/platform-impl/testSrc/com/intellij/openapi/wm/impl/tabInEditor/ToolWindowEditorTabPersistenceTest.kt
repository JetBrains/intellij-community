// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabActionProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.registryKeyFixture
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests the persistence pipeline of a tool window editor tab: the persistent file that a move to the editor creates,
 * the editor state that the platform stores for the tab, the XML form of that state, and the VFS URL that the platform
 * resolves when it restores the tab.
 */
@TestApplication
class ToolWindowEditorTabPersistenceTest {
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
  private val registry: ToolWindowEditorTabFileRegistry
    get() = ToolWindowEditorTabFileRegistry.getInstance()

  private val toolWindowId = "TestToolWindow"
  private lateinit var provider: FakeToolWindowEditorTabPersistenceProvider

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    registryFixture.get()
    manager.closeAllFiles()
    ExtensionTestUtil.maskExtensions(ContentTabActionProvider.EP_NAME, emptyList(), disposable)
    provider = FakeToolWindowEditorTabPersistenceProvider()
    registerSupport(toolWindowId)
    registerFakeToolWindowEditorTabPersistenceProvider(toolWindowId, provider, disposable)
  }

  @AfterEach
  fun tearDown() {
    // The registry is an application service, so the files of this project must not leak into the next test.
    registry.removeFilesForProject(project.locationHash)
  }

  private fun registerSupport(id: String) {
    registerFakeToolWindowEditorTabSupport(id, FakeToolWindowEditorTabSupport(flowOf(ToolWindowEditorTabPresentation("Tab"))), disposable)
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

  private fun openTabFiles(): List<ToolWindowEditorTabFile> = manager.openFiles.filterIsInstance<ToolWindowEditorTabFile>()

  /**
   * Moves a new content to the editor and returns it with its tab file.
   *
   * The editor manager records the editor state when it opens the tab, so the serialize calls of that open are
   * dropped. A test then sees only the calls it causes itself.
   */
  private fun moveToEditor(displayName: String = "tab"): Pair<Content, ToolWindowEditorTabFile> {
    val toolWindow = createToolWindow()
    val content = addContent(toolWindow, displayName)
    controller.moveContentToEditor(toolWindow, content)
    provider.serializeInvocations.clear()
    return content to openTabFiles().single { it.attachedContent(project) === content }
  }

  private fun selectedEditor(file: ToolWindowEditorTabFile): FileEditor = requireNotNull(manager.getSelectedEditor(file))

  private fun tabUrl(path: PersistentToolWindowEditorTabPath): String =
    VirtualFileManager.constructUrl(getToolWindowEditorTabFileSystem().protocol, path.toString())

  private fun findFileByUrl(url: String): VirtualFile? = VirtualFileManager.getInstance().findFileByUrl(url)

  // Persistent file creation

  @Test
  fun `moving content to the editor creates a persistent file when the provider can serialize it`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val (content, tabFile) = moveToEditor()

      val path = requireNotNull(tabFile.persistentPath) { "A tab with a persistence provider must get a persistent path" }
      assertThat(path.toolWindowId).isEqualTo(toolWindowId)
      assertThat(path.projectLocationHash).isEqualTo(project.locationHash)
      assertThat(registry.findFile(path)).isSameAs(tabFile)
      assertThat(tabFile.isPersistedInEditorHistory()).isTrue()
      assertThat(tabFile.attachedContent(project)).isSameAs(content)
    }

  @Test
  fun `each moved content gets its own persistent identity`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val (_, firstFile) = moveToEditor(displayName = "tab")
      val (_, secondFile) = moveToEditor(displayName = "tab")

      assertThat(secondFile).isNotSameAs(firstFile)
      assertThat(secondFile.persistentPath).isNotEqualTo(firstFile.persistentPath)
      assertThat(openTabFiles()).containsExactlyInAnyOrder(firstFile, secondFile)
    }

  @Test
  fun `a tab is transient when its content cannot be persisted`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val idWithoutProvider = "ToolWindowWithoutProvider"
      registerSupport(idWithoutProvider)
      val toolWindowWithoutProvider = createToolWindow(idWithoutProvider)
      val contentWithoutProvider = addContent(toolWindowWithoutProvider)

      val idWithRefusingProvider = "ToolWindowWithRefusingProvider"
      registerSupport(idWithRefusingProvider)
      registerFakeToolWindowEditorTabPersistenceProvider(
        idWithRefusingProvider,
        FakeToolWindowEditorTabPersistenceProvider(canSerializeResult = false),
        disposable,
      )
      val toolWindowWithRefusingProvider = createToolWindow(idWithRefusingProvider)
      val refusedContent = addContent(toolWindowWithRefusingProvider)

      controller.moveContentToEditor(toolWindowWithoutProvider, contentWithoutProvider)
      controller.moveContentToEditor(toolWindowWithRefusingProvider, refusedContent)

      val tabFiles = openTabFiles()
      assertThat(tabFiles).hasSize(2)
      assertThat(tabFiles).allSatisfy { file ->
        assertThat(file.persistentPath).isNull()
        assertThat(file.isPersistedInEditorHistory()).isFalse()
      }
    }

  @Test
  fun `closing a persistent tab removes it from the registry and the editor history`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val (_, tabFile) = moveToEditor()
      val path = requireNotNull(tabFile.persistentPath)
      val history = EditorHistoryManager.getInstance(project)
      waitUntil("the open tab should enter the editor history") { history.hasBeenOpen(tabFile) }

      manager.closeFile(tabFile)

      assertThat(manager.isFileOpen(tabFile)).isFalse()
      assertThat(tabFile.isValid).isFalse()
      assertThat(tabFile.session(project)).isNull()
      // A closed tab must not come back through its path, and Recent Files must not list it.
      assertThat(registry.findFile(path)).isNull()
      assertThat(history.hasBeenOpen(tabFile)).isFalse()
    }

  // Editor state

  @Test
  fun `the editor state of a persistent tab is the serialized content`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val (content, tabFile) = moveToEditor()

      val state = selectedEditor(tabFile).getState(FileEditorStateLevel.FULL)

      assertThat(provider.serializeInvocations).containsExactly(content)
      assertThat(state).isInstanceOf(ToolWindowEditorTabState::class.java)
      assertThat((state as ToolWindowEditorTabState).contentState.name).isEqualTo("fake-state")
    }

  @Test
  fun `a transient tab has no editor state even if its tool window has a provider`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val transientFile = createTabFile(project = project, toolWindowId = toolWindowId)
      manager.openFile(transientFile, true)

      val state = selectedEditor(transientFile).getState(FileEditorStateLevel.FULL)

      assertThat(state).isSameAs(FileEditorState.INSTANCE)
      assertThat(provider.serializeInvocations).isEmpty()
    }

  @Test
  fun `a persistent tab has no editor state once its content cannot be serialized`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val (_, tabFile) = moveToEditor()
      provider.canSerializeResult = false

      val state = selectedEditor(tabFile).getState(FileEditorStateLevel.FULL)

      assertThat(state).isSameAs(FileEditorState.INSTANCE)
      assertThat(provider.serializeInvocations).isEmpty()
    }

  // XML form of the editor state

  @Test
  fun `the editor provider writes the content state and reads it back`() {
    val editorProvider = ToolWindowEditorTabFileEditorProvider()
    val contentState = Element("content").setAttribute("name", "Tab 1")
    val target = Element("state")

    editorProvider.writeState(ToolWindowEditorTabState(contentState), project, target)
    val readState = editorProvider.readState(target, project, lazyOf(null))

    assertThat(readState).isInstanceOf(ToolWindowEditorTabState::class.java)
    val readContentState = (readState as ToolWindowEditorTabState).contentState
    assertThat(JDOMUtil.areElementsEqual(readContentState, contentState)).isTrue()
    // Both directions clone, so neither the stored element nor the read element is attached to the workspace XML.
    assertThat(contentState.parent).isNull()
    assertThat(readContentState.parent).isNull()
  }

  @Test
  fun `the editor provider reads no state unless exactly one content element is stored`() {
    val editorProvider = ToolWindowEditorTabFileEditorProvider()
    val withoutContent = Element("state")
    val withTwoContents = Element("state").addContent(Element("first")).addContent(Element("second"))

    assertThat(editorProvider.readState(withoutContent, project, lazyOf(null))).isSameAs(FileEditorState.INSTANCE)
    assertThat(editorProvider.readState(withTwoContents, project, lazyOf(null))).isSameAs(FileEditorState.INSTANCE)
  }

  @Test
  fun `the editor provider writes the same state twice and skips a foreign state`() {
    val editorProvider = ToolWindowEditorTabFileEditorProvider()
    val state = ToolWindowEditorTabState(Element("content"))
    val firstTarget = Element("state")
    val secondTarget = Element("state")
    val foreignTarget = Element("state")

    editorProvider.writeState(state, project, firstTarget)
    editorProvider.writeState(state, project, secondTarget)
    editorProvider.writeState(FileEditorState.INSTANCE, project, foreignTarget)

    assertThat(firstTarget.children).hasSize(1)
    assertThat(secondTarget.children).hasSize(1)
    assertThat(foreignTarget.children).isEmpty()
  }

  // VFS URL

  @Test
  fun `the URL of a persistent tab resolves to the same file`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val (_, tabFile) = moveToEditor()

      assertThat(tabFile.fileSystem).isInstanceOf(ToolWindowEditorTabFileSystem::class.java)
      assertThat(tabFile.path).isEqualTo(tabFile.persistentPath.toString())
      assertThat(findFileByUrl(tabFile.url)).isSameAs(tabFile)
      assertThat(VirtualFileManager.getInstance().refreshAndFindFileByUrl(tabFile.url)).isSameAs(tabFile)
    }

  @Test
  fun `a stored URL resolves to a file without content before the tab is restored`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val path = PersistentToolWindowEditorTabPath(project.locationHash, toolWindowId, "stored-tab", name = "Stored")

      val file = findFileByUrl(tabUrl(path))

      assertThat(file).isInstanceOf(ToolWindowEditorTabFile::class.java)
      val tabFile = file as ToolWindowEditorTabFile
      assertThat(tabFile.persistentPath).isEqualTo(path)
      assertThat(tabFile.name).isEqualTo("Stored")
      assertThat(tabFile.session(project)).isNull()
      // The splitters and the editor history resolve the same URL independently and must share the file.
      assertThat(findFileByUrl(tabUrl(path))).isSameAs(tabFile)
    }

  @Test
  fun `a stored URL does not resolve while the feature is disabled`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val path = PersistentToolWindowEditorTabPath(project.locationHash, toolWindowId, "stored-tab", name = "Stored")

      val registryValue = Registry.get(ToolWindowEditorTabSupportUtil.REGISTRY_KEY)
      registryValue.setValue(false)
      try {
        assertThat(findFileByUrl(tabUrl(path))).isNull()
      }
      finally {
        registryValue.setValue(true)
      }
    }

  @Test
  fun `a stored URL of a tool window without a provider does not resolve`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val path = PersistentToolWindowEditorTabPath(project.locationHash, "ToolWindowWithoutProvider", "stored-tab", name = "Stored")

      assertThat(findFileByUrl(tabUrl(path))).isNull()
    }

  @Test
  fun `a transient tab has no URL in the tab file system`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val transientFile = createTabFile(project = project, toolWindowId = toolWindowId)

      assertThat(transientFile.fileSystem).isNotInstanceOf(ToolWindowEditorTabFileSystem::class.java)
      assertThat(transientFile.url).doesNotStartWith(getToolWindowEditorTabFileSystem().protocol)
    }
}
