// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.actions.CloseAction.CloseTarget
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.registryKeyFixture
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabActionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class ToolWindowEditorTabPreCloseCheckTest {
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
  private val manager: FileEditorManagerImpl get() = fileEditorManagerFixture.get()
  private val controller: ToolWindowEditorTabTransferController
    get() = ToolWindowEditorTabTransferController.getInstance(project)

  private val toolWindowId = "TestToolWindow"

  private fun createDetachedTabFile(displayName: String = "tab"): ToolWindowEditorTabFile = createTabFile(
    project = project,
    toolWindowId = toolWindowId,
    content = createTabContent(displayName = displayName),
    presentationFlow = flowOf(ToolWindowEditorTabPresentation("Title")),
  )

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    registryFixture.get()
    manager.closeAllFiles()
    ExtensionTestUtil.maskExtensions(ContentTabActionProvider.EP_NAME, emptyList(), disposable)
  }

  private fun openTabFile(): ToolWindowEditorTabFile =
    manager.openFiles.filterIsInstance<ToolWindowEditorTabFile>().single()

  @Test
  fun `non tab files can always be closed`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val check = ToolWindowEditorTabPreCloseCheck()
    assertThat(check.canCloseFile(LightVirtualFile("plain.txt"))).isTrue()
  }

  @Test
  fun `tab file without support can be closed`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val check = ToolWindowEditorTabPreCloseCheck()
    assertThat(check.canCloseFile(createDetachedTabFile())).isTrue()
  }

  @Test
  fun `tab file close is delegated to support - allowed`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    registerFakeToolWindowEditorTabSupport(
      toolWindowId,
      FakeToolWindowEditorTabSupport(flowOf(ToolWindowEditorTabPresentation("Title")), canClose = true),
      disposable,
    )
    val check = ToolWindowEditorTabPreCloseCheck()
    assertThat(check.canCloseFile(createDetachedTabFile())).isTrue()
  }

  @Test
  fun `tab file close is delegated to support - blocked`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    registerFakeToolWindowEditorTabSupport(
      toolWindowId,
      FakeToolWindowEditorTabSupport(flowOf(ToolWindowEditorTabPresentation("Title")), canClose = false),
      disposable,
    )
    val check = ToolWindowEditorTabPreCloseCheck()
    val tabFile = createDetachedTabFile()

    assertThat(check.canCloseFile(tabFile)).isFalse()
    // A blocked group vetoes the whole bulk close, even alongside a plain file.
    val plain = LightVirtualFile("plain.txt")
    assertThat(check.canCloseFiles(listOf(plain, tabFile))).isFalse()
    assertThat(check.filterFilesToClose(listOf(plain, tabFile))).containsExactly(plain)
  }

  @Test
  fun `tab files of the same tool window are checked in a single grouped call`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val support = FakeToolWindowEditorTabSupport(flowOf(ToolWindowEditorTabPresentation("Title")), canClose = true)
      registerFakeToolWindowEditorTabSupport(toolWindowId, support, disposable)
      val check = ToolWindowEditorTabPreCloseCheck()
      val tabFile1 = createDetachedTabFile()
      val tabFile2 = createDetachedTabFile()

      assertThat(check.canCloseFiles(listOf(tabFile1, tabFile2, LightVirtualFile("plain.txt")))).isTrue()
      // The support must be asked once for the whole group, not once per tab.
      assertThat(support.filterTabsToCloseInvocations).hasSize(1)
      assertThat(support.filterTabsToCloseInvocations.single())
        .containsExactly(tabFile1.attachedContent(project), tabFile2.attachedContent(project))
    }

  @Test
  fun `bulk close preserves order while dropping only blocked tab files`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val blockedTab = createDetachedTabFile("blocked")
      val closableTab = createDetachedTabFile("closable")
      val plain = LightVirtualFile("plain.txt")

      registerFakeToolWindowEditorTabSupport(
        toolWindowId,
        FakeToolWindowEditorTabSupport(
          flowOf(ToolWindowEditorTabPresentation("Title")),
          filterTabsToCloseAction = {
            listOf(requireNotNull(closableTab.attachedContent(project)))
          },
        ),
        disposable,
      )

      val check = ToolWindowEditorTabPreCloseCheck()

      assertThat(check.filterFilesToClose(listOf(blockedTab, plain, closableTab)))
        .containsExactly(plain, closableTab)
      assertThat(check.canCloseFiles(listOf(blockedTab, plain, closableTab))).isFalse()
    }

  /**
   * Test for the editor-tab CloseTarget path used by CloseContent / Cmd+W.
   * Verifies that closing a tool-window editor tab still reaches pre-close checks and
   * invokes ToolWindowEditorTabSupport.filterTabsToClose on the registered support.
   */
  @Test
  fun `close target respects pre close checks for tool window editor tabs`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val blockedToolWindowId = "BlockedToolWindow"
      val support = FakeToolWindowEditorTabSupport(
        flowOf(ToolWindowEditorTabPresentation("Blocked tab")),
        canClose = false,
      )
      registerFakeToolWindowEditorTabSupport(blockedToolWindowId, support, disposable)
      val contentManager = ContentFactory.getInstance().createContentManager(false, project)
      Disposer.register(disposable, contentManager)
      val toolWindow = object : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
        override fun getId(): String = blockedToolWindowId
        override fun getContentManager(): ContentManager = contentManager
      }
      val content = createTabContent()
      toolWindow.contentManager.addContent(content)
      controller.moveContentToEditor(toolWindow, content)
      val tabFile = openTabFile()
      val window = manager.windows.single { it.getComposite(tabFile) != null }
      val closeTarget = window.tabbedPane.editorTabs as CloseTarget

      closeTarget.close()

      assertThat(support.filterTabsToCloseInvocations).containsExactly(listOf(content))
    }
}
