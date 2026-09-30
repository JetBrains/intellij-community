// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.registryKeyFixture
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.docking.DockContainer.ContentResponse
import com.intellij.ui.docking.DockableContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.awt.Image
import javax.swing.JPanel

/**
 * Tests the drop decision of [ToolWindowEditorTabDockContainer]: which dragged editor tabs a tool window accepts.
 *
 * The install and the removal of the container run in a show-driven coroutine of the decorator, so they are not
 * covered here.
 */
@TestApplication
class ToolWindowEditorTabDockContainerTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val projectFixture = projectFixture(openAfterCreation = true)
  private val registryFixture = registryKeyFixture(ToolWindowEditorTabSupportUtil.REGISTRY_KEY) { setValue(true) }

  private val project: Project get() = projectFixture.get()

  private val toolWindowId = "TestToolWindow"

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    registryFixture.get()
    registerFakeToolWindowEditorTabSupport(toolWindowId, FakeToolWindowEditorTabSupport(flowOf(ToolWindowEditorTabPresentation("Tab"))), disposable)
    // The container compares the id of the dragged tab with the id of the registered tool window,
    // and the default headless mock reports an empty id.
    project.replaceService(ToolWindowManager::class.java, IdAwareHeadlessToolWindowManager(project), disposable)
    ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask(id = toolWindowId))
  }

  /**
   * A headless tool window manager whose mock tool windows report the id they were registered with.
   */
  private class IdAwareHeadlessToolWindowManager(private val project: Project) : ToolWindowHeadlessManagerImpl(project) {
    private val toolWindowById = HashMap<String, ToolWindow>()

    override fun doRegisterToolWindow(id: String): ToolWindow = toolWindowById.getOrPut(id) {
      object : MockToolWindow(project) {
        override fun getId(): String = id
      }
    }

    override fun getToolWindow(id: String?): ToolWindow? = toolWindowById[id]
  }

  private fun createContainer(id: String = toolWindowId): ToolWindowEditorTabDockContainer =
    ToolWindowEditorTabDockContainer(project, id, JPanel())

  private fun tabFile(id: String = toolWindowId): ToolWindowEditorTabFile =
    ToolWindowEditorTabFile(toolWindowId = id, persistentPath = null)

  private fun dragged(key: Any): DockableContent<Any> = FakeDockableContent(key)

  @Test
  fun `the container accepts the tab of its own tool window`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    assertThat(createContainer().getContentResponse(dragged(tabFile()), null)).isEqualTo(ContentResponse.ACCEPT_MOVE)
  }

  @Test
  fun `the container denies the tab of another tool window`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    assertThat(createContainer().getContentResponse(dragged(tabFile("OtherToolWindow")), null)).isEqualTo(ContentResponse.DENY)
  }

  @Test
  fun `the container denies a tab when its tool window is not registered`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val unregisteredId = "UnregisteredToolWindow"
      registerFakeToolWindowEditorTabSupport(unregisteredId, FakeToolWindowEditorTabSupport(flowOf(ToolWindowEditorTabPresentation("Tab"))), disposable)

      assertThat(createContainer(unregisteredId).getContentResponse(dragged(tabFile(unregisteredId)), null)).isEqualTo(ContentResponse.DENY)
    }

  @Test
  fun `the container denies a key that is not a tool window editor tab`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val container = createContainer()

      assertThat(container.getContentResponse(dragged(LightVirtualFile("plain.txt")), null)).isEqualTo(ContentResponse.DENY)
      assertThat(container.getContentResponse(dragged("not a file"), null)).isEqualTo(ContentResponse.DENY)
    }

  @Test
  fun `the container denies every tab while the feature is disabled`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val registryValue = Registry.get(ToolWindowEditorTabSupportUtil.REGISTRY_KEY)
      registryValue.setValue(false)
      try {
        assertThat(createContainer().getContentResponse(dragged(tabFile()), null)).isEqualTo(ContentResponse.DENY)
      }
      finally {
        registryValue.setValue(true)
      }
    }

  /**
   * The dragged editor tab as the dock manager hands it to a container. Only the key matters for the drop decision.
   */
  private class FakeDockableContent(private val key: Any) : DockableContent<Any> {
    override fun getKey(): Any = key
    override fun getPresentation(): Presentation = Presentation("dragged")
    override fun getDockContainerType(): String = "fake"
    override fun getPreferredSize(): Dimension = Dimension()
    override fun getPreviewImage(): Image? = null
    override fun close() {}
  }
}
