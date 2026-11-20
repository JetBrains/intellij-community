// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.MultiProjectToolWindowFactory
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.RegisterToolWindowTaskData
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowPaneOldButtonManager
import com.intellij.toolWindow.computeToolWindowBeans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

private const val ID = "FrameGuestTestToolWindow"
private const val MULTI_PROJECT_ID = "FrameGuestTestMultiProjectToolWindow"
private const val SPLIT_KEY = "ide.frame.guests.legacy.tool.windows"

/**
 * The tool windows that a frame guest registers, and how its manager registers one that the frame owner presents.
 * A headless test cannot create a frame, so the managers have no tool window pane.
 */
@TestApplication
internal class FrameGuestToolWindowRegistrationTest {
  private val owner by projectFixture()
  private val guest by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    askedProjects.clear()
  }

  @Test
  @RegistryKey(key = SPLIT_KEY, value = "true")
  fun `a frame guest registers only the tool windows that show one project`(): Unit = timeoutRunBlocking {
    maskToolWindows()
    joinFrame()

    val tasks = computeToolWindowBeans(guest)

    assertThat(tasks.map { it.id }).containsExactly(ID)
    assertThat(tasks.single().singleProject).isTrue()
    assertThat(askedProjects).describedAs("a factory out of the scope is not asked whether it is applicable").containsExactly(ID to guest.name)
  }

  @Test
  @RegistryKey(key = SPLIT_KEY, value = "true")
  fun `a frame owner without content of its own registers only the tool windows with the marker`(): Unit = timeoutRunBlocking {
    maskToolWindows()
    ExtensionTestUtil.maskExtensions(
      ProjectFrameCapabilitiesService.EP_NAME,
      listOf(object : ProjectFrameCapabilitiesProvider {
        override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
          return if (project === owner) setOf(ProjectFrameCapability.SUPPRESS_OWN_TOOL_WINDOW_CONTENT) else emptySet()
        }

        override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? = null
      }),
      disposable,
    )

    val tasks = computeToolWindowBeans(owner)

    assertThat(tasks.map { it.id }).containsExactly(MULTI_PROJECT_ID)
    assertThat(tasks.single().singleProject).isFalse()
    assertThat(askedProjects).containsExactly(MULTI_PROJECT_ID to owner.name)
  }

  @Test
  @RegistryKey(key = SPLIT_KEY, value = "true")
  fun `a frame owner with content of its own registers every tool window`(): Unit = timeoutRunBlocking {
    maskToolWindows()
    joinFrame()

    val tasks = computeToolWindowBeans(owner)

    assertThat(tasks.map { it.id }).containsExactlyInAnyOrder(ID, MULTI_PROJECT_ID)
  }

  @Test
  fun `without the legacy mode the scope of a frame guest has every tool window`(): Unit = timeoutRunBlocking {
    maskToolWindows()
    joinFrame()

    val tasks = computeToolWindowBeans(guest)

    assertThat(tasks.map { it.id }).containsExactlyInAnyOrder(ID, MULTI_PROJECT_ID)
  }

  @Test
  fun `a presented tool window is registered without a stripe button and stays hidden`(): Unit = timeoutRunBlocking {
    val ownerManager = createManager(owner)
    val guestManager = createManager(guest)
    guestManager.initAsFrameGuest(ownerManager.presentation)
    withContext(Dispatchers.EDT) {
      // a layout that the guest stored for a frame of its own has no meaning in the frame of the owner
      guestManager.layoutState.addInfo(ID, visibleInfo(ID))

      val entry = guestManager.registerToolWindow(singleProjectTask(), buttonManager(guestManager))

      assertThat(entry.toolWindow.isPresented).isTrue()
      assertThat(entry.stripeButton).isNull()
      assertThat(entry.toolWindow.isVisible).isFalse()
      assertThat(guestManager.getToolWindow(ID)).isSameAs(entry.toolWindow)
      assertThat(entry.toolWindow.project).isSameAs(guest)
      assertThat(ownerManager.getEntry(ID)!!.stripeButton).describedAs("the frame owner keeps the stripe button").isNotNull()
    }
  }

  @Test
  fun `a tool window that code registers in a frame guest is presented`(): Unit = timeoutRunBlocking {
    val ownerManager = createManager(owner)
    val guestManager = createManager(guest)
    guestManager.initAsFrameGuest(ownerManager.presentation)
    withContext(Dispatchers.EDT) {
      val toolWindow = guestManager.registerToolWindow(RegisterToolWindowTask(id = ID, anchor = ToolWindowAnchor.LEFT)) as ToolWindowImpl

      assertThat(toolWindow.isPresented).isTrue()
      assertThat(toolWindow.project).isSameAs(guest)
      assertThat(guestManager.getToolWindow(ID)).isSameAs(toolWindow)
      assertThat(guestManager.getEntry(ID)!!.stripeButton).isNull()
      assertThat(ownerManager.getEntry(ID)!!.stripeButton).describedAs("the frame owner keeps the stripe button").isNotNull()
    }
  }

  @Test
  fun `the marker keeps a tool window that code registers in a frame guest out of the presentation`(): Unit = timeoutRunBlocking {
    val guestManager = createManager(guest)
    guestManager.initAsFrameGuest(createManager(owner).presentation)
    val task = RegisterToolWindowTaskData(id = ID, anchor = ToolWindowAnchor.LEFT)

    assertThat(guestManager.toCodeRegistration(task).singleProject).describedAs("no factory").isTrue()
    assertThat(guestManager.toCodeRegistration(task.copy(contentFactory = SingleProjectTestFactory())).singleProject).isTrue()
    assertThat(guestManager.toCodeRegistration(task.copy(contentFactory = MultiProjectTestFactory())).singleProject).isFalse()
  }

  @Test
  fun `a tool window that code registers in a frame owner keeps its task`(): Unit = timeoutRunBlocking {
    val ownerManager = createManager(owner)
    createManager(guest).initAsFrameGuest(ownerManager.presentation)
    val task = RegisterToolWindowTaskData(id = ID, anchor = ToolWindowAnchor.LEFT)

    assertThat(ownerManager.toCodeRegistration(task)).isSameAs(task)
  }

  @Test
  fun `a new layout of a frame guest leaves a presented tool window alone`(): Unit = timeoutRunBlocking {
    val guestManager = createManager(guest)
    guestManager.initAsFrameGuest(createManager(owner).presentation)
    withContext(Dispatchers.EDT) {
      val toolWindow = guestManager.registerToolWindow(singleProjectTask(), buttonManager(guestManager)).toolWindow

      guestManager.setLayout(DesktopLayout().also { it.addInfo(ID, visibleInfo(ID)) })

      assertThat(toolWindow.isVisible).isFalse()
      assertThat(guestManager.getEntry(ID)!!.stripeButton).isNull()
    }
  }

  @Test
  fun `the removal of a presented tool window keeps the activation action of the frame owner`(): Unit = timeoutRunBlocking {
    val ownerManager = createManager(owner)
    val guestManager = createManager(guest)
    guestManager.initAsFrameGuest(ownerManager.presentation)
    withContext(Dispatchers.EDT) {
      ownerManager.registerToolWindow(singleProjectTask(), buttonManager(ownerManager))
      guestManager.registerToolWindow(singleProjectTask(), buttonManager(guestManager))
      assertThat(ActionManager.getInstance().getAction(activateActionId())).isNotNull()

      guestManager.doUnregisterToolWindow(ID)

      assertThat(guestManager.getToolWindow(ID)).isNull()
      assertThat(ActionManager.getInstance().getAction(activateActionId())).isNotNull()
    }
  }

  private fun maskToolWindows() {
    ExtensionTestUtil.maskExtensions(
      ToolWindowEP.EP_NAME,
      listOf(toolWindowEp(ID, SingleProjectTestFactory::class.java), toolWindowEp(MULTI_PROJECT_ID, MultiProjectTestFactory::class.java)),
      disposable,
      fireEvents = false,
    )
  }

  /** The record of the test window manager: [guest] is a frame guest of [owner]. */
  private fun joinFrame() {
    val windowManager = WindowManagerEx.getInstanceEx()
    windowManager.addFrameGuest(owner, guest)
    Disposer.register(disposable) { windowManager.removeFrameGuest(guest) }
  }

  private fun createManager(project: Project): ToolWindowManagerImpl {
    val state = ToolWindowManagerStateImpl()
    state.noStateLoaded()
    project.replaceService(ToolWindowManagerState::class.java, state, disposable)

    val buttonManager = ToolWindowPaneOldButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
    val coroutineScope = (project as ComponentManagerEx).getCoroutineScope()
    val manager = object : ToolWindowManagerImpl(project, isNewUi = false, isEdtRequired = false, coroutineScope = coroutineScope) {
      override fun getButtonManager(toolWindow: ToolWindow): ToolWindowButtonManager = buttonManager

      override fun getButtonManager(paneId: String): ToolWindowButtonManager = buttonManager
    }
    Disposer.register(disposable, manager)
    Disposer.register(disposable) {
      val actionManager = ActionManager.getInstance()
      if (actionManager.getAction(activateActionId()) != null) {
        actionManager.unregisterAction(activateActionId())
      }
    }
    return manager
  }
}

/** The tool window id and the project name of each `isApplicableAsync` call. A name, because a static field must not hold a project. */
private val askedProjects = CopyOnWriteArrayList<Pair<String, String>>()

private fun activateActionId(): String = ActivateToolWindowAction.Manager.getActionIdForToolWindow(ID)

private fun buttonManager(manager: ToolWindowManagerImpl): ToolWindowButtonManager {
  return manager.getButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
}

private fun singleProjectTask(): RegisterToolWindowTaskData {
  return RegisterToolWindowTaskData(
    id = ID,
    anchor = ToolWindowAnchor.LEFT,
    contentFactory = SingleProjectTestFactory(),
    singleProject = true,
  )
}

private fun visibleInfo(id: String): WindowInfoImpl {
  return WindowInfoImpl().also {
    it.id = id
    it.anchor = ToolWindowAnchor.LEFT
    it.isVisible = true
  }
}

private fun toolWindowEp(id: String, factoryClass: Class<out ToolWindowFactory>): ToolWindowEP {
  return ToolWindowEP().also {
    it.pluginDescriptor = DefaultPluginDescriptor("test")
    it.id = id
    it.anchor = ToolWindowAnchor.LEFT.toString()
    it.factoryClass = factoryClass.name
  }
}

internal class SingleProjectTestFactory : ToolWindowFactory {
  override suspend fun isApplicableAsync(project: Project): Boolean {
    askedProjects.add(ID to project.name)
    return true
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
  }
}

internal class MultiProjectTestFactory : MultiProjectToolWindowFactory {
  override suspend fun isApplicableAsync(project: Project): Boolean {
    askedProjects.add(MULTI_PROJECT_ID to project.name)
    return true
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
  }
}
