// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

private const val ID = "FrameGuestTestToolWindow"
private const val MULTI_PROJECT_ID = "FrameGuestTestMultiProjectToolWindow"
private const val SHOWN_ID = "FrameGuestTestShownToolWindow"
private const val LEGACY_TOOL_WINDOWS_KEY = "ide.frame.guests.legacy.tool.windows"

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

  /** The managers of the test. The IDE disposes each manager on the EDT, with its project, so the test does the same. */
  private val createdManagers = ArrayList<ToolWindowManagerImpl>()

  @AfterEach
  fun disposeManagers(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      for (manager in createdManagers.asReversed()) {
        Disposer.dispose(manager)
      }
    }
  }

  @BeforeEach
  fun setUp() {
    askedProjects.clear()
    otherToolWindowLookups.clear()
  }

  @Test
  @RegistryKey(key = LEGACY_TOOL_WINDOWS_KEY, value = "true")
  fun `a frame guest registers only the tool windows that show one project`(): Unit = timeoutRunBlocking {
    maskToolWindows()
    joinFrame()

    val tasks = computeToolWindowBeans(guest)

    assertThat(tasks.map { it.id }).containsExactly(ID)
    assertThat(askedProjects).describedAs("a factory out of the scope is not asked whether it is applicable").containsExactly(ID to guest.name)
  }

  @Test
  @RegistryKey(key = LEGACY_TOOL_WINDOWS_KEY, value = "true")
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
    assertThat(askedProjects).containsExactly(MULTI_PROJECT_ID to owner.name)
  }

  @Test
  @RegistryKey(key = LEGACY_TOOL_WINDOWS_KEY, value = "true")
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
  fun `the marker keeps a tool window of a frame guest out of the presentation`(): Unit = timeoutRunBlocking {
    val guestManager = createManager(guest)
    guestManager.initAsFrameGuest(createManager(owner).presentation)
    val task = RegisterToolWindowTaskData(id = ID, anchor = ToolWindowAnchor.LEFT)

    withContext(Dispatchers.EDT) {
      assertThat(guestManager.prepareRegisterToolWindowTask(task).isPresented).describedAs("no factory").isTrue()
      assertThat(guestManager.prepareRegisterToolWindowTask(task.copy(contentFactory = SingleProjectTestFactory())).isPresented).isTrue()
      assertThat(guestManager.prepareRegisterToolWindowTask(task.copy(contentFactory = MultiProjectTestFactory())).isPresented).isFalse()
    }
  }

  @Test
  fun `a tool window that a frame owner registers is not presented`(): Unit = timeoutRunBlocking {
    val ownerManager = createManager(owner)
    createManager(guest).initAsFrameGuest(ownerManager.presentation)
    val task = RegisterToolWindowTaskData(id = ID, anchor = ToolWindowAnchor.LEFT)

    withContext(Dispatchers.EDT) {
      assertThat(ownerManager.prepareRegisterToolWindowTask(task).isPresented).isFalse()
      assertThat(ownerManager.prepareRegisterToolWindowTask(task.copy(contentFactory = SingleProjectTestFactory())).isPresented).isFalse()
    }
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
  fun `the presentation of a tool window leaves the stored layout of the frame guest alone`(): Unit = timeoutRunBlocking {
    val ownerManager = createManager(owner)
    val guestManager = createManager(guest)
    guestManager.initAsFrameGuest(ownerManager.presentation)
    withContext(Dispatchers.EDT) {
      // the layout that the guest stored for a frame of its own, and the layout of the frame owner
      guestManager.layoutState.addInfo(ID, visibleInfo(ID).also { it.anchor = ToolWindowAnchor.BOTTOM })
      ownerManager.layoutState.addInfo(ID, visibleInfo(ID).also { it.anchor = ToolWindowAnchor.RIGHT })

      val toolWindow = guestManager.registerToolWindow(singleProjectTask(), buttonManager(guestManager)).toolWindow
      assertThat(toolWindow.isVisible).describedAs("the layout of the frame owner shows the tool window").isTrue()
      assertThat(toolWindow.anchor).isEqualTo(ToolWindowAnchor.RIGHT)
      toolWindow.hide()
      assertThat(toolWindow.isVisible).isFalse()

      val stored = guestManager.layoutState.getInfo(ID)!!
      assertThat(stored.anchor).isEqualTo(ToolWindowAnchor.BOTTOM)
      assertThat(stored.isVisible).isTrue()
    }
  }

  @Test
  fun `the presentation of a tool window adds no info to the layout of the frame guest`(): Unit = timeoutRunBlocking {
    val ownerManager = createManager(owner)
    val guestManager = createManager(guest)
    guestManager.initAsFrameGuest(ownerManager.presentation)
    withContext(Dispatchers.EDT) {
      val toolWindow = guestManager.registerToolWindow(singleProjectTask(), buttonManager(guestManager)).toolWindow

      toolWindow.show()
      toolWindow.activate(null)
      toolWindow.hide()

      assertThat(guestManager.layoutState.getInfo(ID)).isNull()
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

  @Test
  @RegistryKey(key = LEGACY_TOOL_WINDOWS_KEY, value = "true")
  fun `a show request of a listener of the registration event shows the tool window of the frame guest`(): Unit = timeoutRunBlocking {
    ExtensionTestUtil.maskExtensions(
      ToolWindowEP.EP_NAME,
      listOf(toolWindowEp(SHOWN_ID, OtherToolWindowLookupFactory::class.java), toolWindowEp(ID, SingleProjectTestFactory::class.java)),
      disposable,
      fireEvents = false,
    )
    joinFrame()
    val ownerManager = createManager(owner)
    val guestManager = createManager(guest)
    guestManager.initAsFrameGuest(ownerManager.presentation)
    // the content of a tool window asks the manager of its project for another tool window
    guest.replaceService(ToolWindowManager::class.java, guestManager, disposable)
    unregisterActivationActionLater(SHOWN_ID)
    // the frame owner has registered its tool windows, and its stored layout shows one tool window of the frame guest at once
    ownerManager.toolWindowSetInitializer.initUi(reopeningEditorJob = Job().also { it.complete() }, taskListDeferred = null)
    withContext(Dispatchers.EDT) {
      // another side than the tool window of the show request: a tool window hides the one that is on its side
      ownerManager.layoutState.addInfo(SHOWN_ID, visibleInfo(SHOWN_ID).also { it.anchor = ToolWindowAnchor.BOTTOM })
    }
    // As the listener of the Learn tool window, which queues the show in the event. The wait makes the order fixed:
    // the request runs after the registration and before the next step of the initialization.
    guest.messageBus.connect(disposable).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
        if (ID in ids) {
          ApplicationManager.getApplication().invokeAndWait { toolWindowManager.getToolWindow(ID)!!.show() }
        }
      }
    })

    guestManager.toolWindowSetInitializer.initPresentedUi(ownerManager.presentation)

    withContext(Dispatchers.EDT) {
      assertThat(ownerManager.getToolWindow(ID)!!.isVisible).describedAs("the frame owner got the show request").isTrue()
      assertThat(ownerManager.presentation.selectedProjectOf(ID)).isSameAs(guest)
      assertThat(guestManager.getToolWindow(ID)!!.isVisible).isTrue()
      assertThat(guestManager.getToolWindow(SHOWN_ID)!!.isVisible).describedAs("the stored layout of the frame owner shows it").isTrue()
      assertThat(otherToolWindowLookups)
        .describedAs("the content of a tool window that shows at once finds the other tool window of the frame guest")
        .containsExactly(true)
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
    createdManagers.add(manager)
    unregisterActivationActionLater(ID)
    return manager
  }

  /** The tool window of a frame owner registers the activation action of its id, and the action stays when the tool window leaves. */
  private fun unregisterActivationActionLater(id: String) {
    Disposer.register(disposable) {
      val actionManager = ActionManager.getInstance()
      val actionId = ActivateToolWindowAction.Manager.getActionIdForToolWindow(id)
      if (actionManager.getAction(actionId) != null) {
        actionManager.unregisterAction(actionId)
      }
    }
  }
}

/** The tool window id and the project name of each `isApplicableAsync` call. A name, because a static field must not hold a project. */
private val askedProjects = CopyOnWriteArrayList<Pair<String, String>>()

/** The answers of [OtherToolWindowLookupFactory]: whether its content found the tool window [ID] of its project. */
private val otherToolWindowLookups = CopyOnWriteArrayList<Boolean>()

private fun activateActionId(): String = ActivateToolWindowAction.Manager.getActionIdForToolWindow(ID)

private fun buttonManager(manager: ToolWindowManagerImpl): ToolWindowButtonManager {
  return manager.getButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
}

private fun singleProjectTask(): RegisterToolWindowTaskData {
  return RegisterToolWindowTaskData(
    id = ID,
    anchor = ToolWindowAnchor.LEFT,
    contentFactory = SingleProjectTestFactory(),
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

/** A factory whose content asks for the tool window [ID] of its project, as content that shows another tool window does. */
internal class OtherToolWindowLookupFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    otherToolWindowLookups.add(ToolWindowManager.getInstance(project).getToolWindow(ID) != null)
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
