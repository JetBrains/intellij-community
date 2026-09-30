// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.registryKeyFixture
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.ComponentUtil
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.util.ui.withForcedRespectIsShowingClientProperty
import com.intellij.util.ui.withShowingChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Tests that a persisted tool window editor tab creates its content only when the content is necessary:
 * when the editor is shown for the first time, or when an operation needs the content.
 */
@TestApplication
class ToolWindowEditorTabPendingContentTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val registryFixture = registryKeyFixture(ToolWindowEditorTabSupportUtil.REGISTRY_KEY) { setValue(true) }

  private val project: Project get() = projectFixture.get()
  private val tabManager: ToolWindowEditorTabManager get() = ToolWindowEditorTabManager.getInstance(project)

  private val toolWindowId = "TestToolWindow"
  private val storedState = ToolWindowEditorTabState(Element("stored-state"))
  private val restoredContent: Content by lazy { createTabContent(displayName = "restored") }
  private val provider = FakeToolWindowEditorTabPersistenceProvider(deserializeAction = { _, _ -> restoredContent })
  private lateinit var fileEditorManager: RecordingFileEditorManager

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    registryFixture.get()
    registerSupportAndProvider(toolWindowId, provider)
    fileEditorManager = RecordingFileEditorManager(project)
    project.replaceService(FileEditorManager::class.java, fileEditorManager, disposable)
  }

  @Test
  fun `a restored tab keeps its state and creates no content before it is shown`(): Unit = uiTest {
    val editor = createRestoredTabEditor()

    assertThat(provider.deserializeInvocations).isEmpty()
    assertThat(editor.file.session(project)).isNull()
    assertThat(editor.getState(FileEditorStateLevel.FULL)).isSameAs(storedState)
    assertThat((editor.component as Wrapper).isNull).isTrue()
  }

  @Test
  fun `the first show of a restored tab creates its content in the placeholder`(): Unit = uiTest {
    val editor = createRestoredTabEditor()
    val placeholder = editor.component as Wrapper

    show(placeholder)
    waitUntil("the content should be restored") { editor.file.session(project) != null }

    assertThat(provider.deserializeInvocations).containsExactly(storedState.contentState)
    assertThat(editor.file.attachedContent(project)).isSameAs(restoredContent)
    assertThat(editor.component).isSameAs(placeholder)
    assertThat(placeholder.targetComponent).isSameAs(restoredContent.component)
    assertThat(editor.preferredFocusedComponent).isSameAs(restoredContent.component)
    assertThat(tabManager.getPendingState(editor.file)).isNull()
  }

  @Test
  fun `the editor state of a restored tab is serialized from its restored content`(): Unit = uiTest {
    val editor = createRestoredTabEditor()
    show(editor.component)
    waitUntil("the content should be restored") { editor.file.session(project) != null }

    val state = editor.getState(FileEditorStateLevel.FULL)

    // The stored state is stale once the content exists: the next save must describe the live content.
    assertThat(provider.serializeInvocations).containsExactly(restoredContent)
    assertThat(state).isNotSameAs(storedState)
    assertThat((state as ToolWindowEditorTabState).contentState.name).isEqualTo("fake-state")
  }

  @Test
  fun `a restored tab shows its stored name before its content is restored`(): Unit = uiTest {
    val editor = createRestoredTabEditor()

    assertThat(editor.name).isEqualTo("Restored")
    assertThat(ToolWindowEditorTabTitleProvider().getEditorTabTitle(project, editor.file)).isEqualTo("Restored")
    assertThat(editor.file.name).isEqualTo("Restored")
    // The presentable URL hides the serialized path from the UI.
    assertThat(editor.file.presentableUrl).isEqualTo("Restored")
    assertThat(provider.deserializeInvocations).isEmpty()
  }

  @Test
  fun `the restored content renames the tab and stores the new name`(): Unit = uiTest {
    val editor = createRestoredTabEditor()

    show(editor.component)
    waitUntil("the tab should take the title of the restored content") { editor.name == "Tab" }

    assertThat(ToolWindowEditorTabTitleProvider().getEditorTabTitle(project, editor.file)).isEqualTo("Tab")
    assertThat(editor.file.name).isEqualTo("Tab")
    assertThat(editor.file.presentableUrl).isEqualTo("Tab")
    // The new name goes into the persistent path, so the next restore shows it before the content is restored.
    assertThat(requireNotNull(editor.file.persistentPath).name).isEqualTo("Tab")
  }

  @Test
  fun `the editor of a tab moved from the tool window shows its content at once`(): Unit = uiTest {
    val content = createTabContent(displayName = "moved")
    val file = createTabFile(project = project, toolWindowId = toolWindowId, content = content)
    val editor = ToolWindowEditorTabFileEditor(project, file)
    Disposer.register(disposable, editor)

    assertThat((editor.component as Wrapper).targetComponent).isSameAs(content.component)
    assertThat(editor.preferredFocusedComponent).isSameAs(content.component)
  }

  @Test
  fun `a restored tab is closed when its content cannot be restored`(): Unit = uiTest {
    val failingToolWindowId = "FailingToolWindow"
    val failingProvider = FakeToolWindowEditorTabPersistenceProvider(deserializeAction = { _, _ -> null })
    registerSupportAndProvider(failingToolWindowId, failingProvider)
    val editor = createRestoredTabEditor(failingToolWindowId)

    show(editor.component)
    waitUntil("the editor tab should be closed") { fileEditorManager.closeRequests.isNotEmpty() }

    assertThat(fileEditorManager.closeRequests).containsExactly(editor.file)
    assertThat(failingProvider.deserializeInvocations).containsExactly(storedState.contentState)
  }

  @Test
  fun `a restored tab that is closed before it is shown creates no content`(): Unit = uiTest {
    val editor = createRestoredTabEditor()
    val file = editor.file

    Disposer.dispose(editor)

    assertThat(provider.deserializeInvocations).isEmpty()
    assertThat(tabManager.getPendingState(file)).isNull()
    assertThat(file.isValid).isFalse()
  }

  @Test
  fun `a restored tab moved back to the tool window creates its content first`(): Unit = uiTest {
    val editor = createRestoredTabEditor()
    val toolWindow = createToolWindow()

    ToolWindowEditorTabTransferController.getInstance(project).moveContentToToolWindow(toolWindow, editor.file)

    assertThat(provider.deserializeInvocations).containsExactly(storedState.contentState)
    assertThat(toolWindow.contentManager.contents.toList()).containsExactly(restoredContent)
  }

  @Test
  fun `an editor tab action that supports a restored tab gets its restored content`(): Unit = uiTest {
    ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask(id = toolWindowId))
    val editor = createRestoredTabEditor()
    val action = PendingContentEditorTabAction()
    val event = createEvent(action, editor)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(provider.deserializeInvocations).isEmpty()

    action.actionPerformed(event)

    assertThat(action.performedContents).containsExactly(restoredContent)
  }

  @Test
  fun `an editor tab action is hidden for a restored tab by default`(): Unit = uiTest {
    ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask(id = toolWindowId))
    val editor = createRestoredTabEditor()
    val action = RecordingEditorTabAction()
    val event = createEvent(action, editor)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
    assertThat(provider.deserializeInvocations).isEmpty()
  }

  @Test
  fun `a restored tab shows its stored icon before its content is restored`(@TempDir tempDir: Path): Unit = uiTest {
    val storedIcon = requireNotNull(createSerializableIcon(tempDir).serialized())
    val editor = createRestoredTabEditor(icon = storedIcon)

    val tabIcon = ToolWindowEditorTabFileIconProvider().getIcon(editor.file, 0, project)

    assertThat(tabIcon.serialized()).isEqualTo(storedIcon)
    assertThat(provider.deserializeInvocations).isEmpty()
  }

  private fun registerSupportAndProvider(id: String, provider: ToolWindowEditorTabPersistenceProvider) {
    registerFakeToolWindowEditorTabSupport(id, FakeToolWindowEditorTabSupport(flowOf(ToolWindowEditorTabPresentation("Tab"))), disposable)
    registerFakeToolWindowEditorTabPersistenceProvider(id, provider, disposable)
  }

  /**
   * Creates the editor of a persistent tab and gives it the stored state, as the editor restore at project open does.
   */
  private fun createRestoredTabEditor(id: String = toolWindowId, icon: ByteArray? = null): ToolWindowEditorTabFileEditor {
    val path = PersistentToolWindowEditorTabPath(
      projectLocationHash = project.locationHash,
      toolWindowId = id,
      persistenceId = "restored-tab",
      name = "Restored",
      icon = icon,
    )
    val file = requireNotNull(ToolWindowEditorTabFileRegistry.getInstance().getOrCreatePersistentFile(path))
    val editor = ToolWindowEditorTabFileEditor(project, file)
    Disposer.register(disposable, editor)
    editor.setState(storedState)
    return editor
  }

  /**
   * Adds [component] to a container that the UI coroutine scopes treat as showing, so that `initOnShow` runs.
   */
  private fun show(component: JComponent) {
    val container = JPanel()
    ComponentUtil.forceMarkAsShowing(container, true)
    withShowingChanged { container.add(component) }
  }

  private fun uiTest(action: suspend CoroutineScope.() -> Unit) {
    withForcedRespectIsShowingClientProperty {
      timeoutRunBlocking(context = Dispatchers.UiWithModelAccess, action = action)
    }
  }

  /**
   * A tool window backed by a real [ContentManager]. The headless [ToolWindowHeadlessManagerImpl]
   * does not carry the id into its mock tool window, so the id is overridden explicitly.
   */
  private fun createToolWindow(): ToolWindow {
    val contentManager = ContentFactory.getInstance().createContentManager(false, project)
    Disposer.register(disposable, contentManager)
    return object : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
      override fun getId(): String = toolWindowId
      override fun getContentManager(): ContentManager = contentManager
    }
  }

  private fun createEvent(action: AnAction, editor: FileEditor): AnActionEvent {
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(PlatformDataKeys.FILE_EDITOR, editor)
      .build()
    return TestActionEvent.createTestEvent(action, dataContext)
  }

  private open class RecordingEditorTabAction : ToolWindowEditorTabActionBase() {
    val performedContents: MutableList<Content> = mutableListOf()

    override fun actionPerformed(e: AnActionEvent, content: Content) {
      performedContents += content
    }

    override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content) {
      e.presentation.isEnabledAndVisible = true
    }
  }

  private class PendingContentEditorTabAction : RecordingEditorTabAction() {
    override fun updateForPendingContent(e: AnActionEvent, toolWindow: ToolWindow) {
      e.presentation.isEnabledAndVisible = true
    }
  }
}
