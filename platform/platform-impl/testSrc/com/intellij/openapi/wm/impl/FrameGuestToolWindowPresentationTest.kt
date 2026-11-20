// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.MultiProjectToolWindowFactory
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.RegisterToolWindowTaskData
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowPaneOldButtonManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JPanel
import javax.swing.SwingUtilities

private const val ID = "FrameGuestPresentationTestToolWindow"

/**
 * How the tool window of a frame owner presents the tool windows of its frame guests, see [ToolWindowManagerPresentation].
 * A headless test cannot create a frame, so the managers have no tool window pane, and the test checks the state and the events.
 */
@TestApplication
internal class FrameGuestToolWindowPresentationTest {
  // open projects: the frame record gives only an open guest as the active project
  private val owner by projectFixture(openAfterCreation = true)
  private val first by projectFixture(openAfterCreation = true)
  private val second by projectFixture(openAfterCreation = true)

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `the first tool window of a frame guest makes the frame owner register a presenting tool window`() = onEdt {
    val managers = createManagers()

    val presented = managers.register(first)

    val presenting = managers.owner.getEntry(ID)!!
    assertThat(presenting.toolWindow.isPresented).isFalse()
    assertThat(presenting.toolWindow.isAvailable).isTrue()
    assertThat(presenting.stripeButton).isNotNull()
    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(first)
    assertThat(presented.isVisible).isFalse()
    assertThat(createdContent).describedAs("a hidden tool window has no content").isEmpty()
  }

  @Test
  fun `the first show selects the active project and shows its tool window`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.register(first)
    val secondToolWindow = managers.register(second)
    val events = recordEvents(second)
    setActive(second)

    managers.owner.showToolWindow(ID)

    assertThat(managers.owner.presentation.selectedProjectOf(ID)).isSameAs(second)
    assertThat(secondToolWindow.isVisible).isTrue()
    assertThat(firstToolWindow.isVisible).isFalse()
    assertThat(managers.owner.presentation.shownToolWindowOf(ID)).isSameAs(secondToolWindow)
    assertThat(events).containsExactly("shown", ToolWindowManagerEventType.ShowToolWindow.name)
    assertThat(createdContent).describedAs("only the shown tool window gets its content").containsExactly(second.name)
  }

  @Test
  fun `the selection does not follow the active project`() = onEdt {
    val managers = createManagers()
    managers.register(first)
    val secondToolWindow = managers.register(second)
    setActive(second)
    managers.owner.showToolWindow(ID)

    setActive(first)
    managers.owner.hideToolWindow(ID)
    managers.owner.showToolWindow(ID)

    assertThat(managers.owner.presentation.selectedProjectOf(ID)).isSameAs(second)
    assertThat(secondToolWindow.isVisible).isTrue()
  }

  @Test
  fun `a select moves the visible state to the tool window of the project`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.register(first)
    val secondToolWindow = managers.register(second)
    setActive(second)
    managers.owner.showToolWindow(ID)
    val events = recordEvents(second)

    managers.owner.presentation.select(ID, first)

    assertThat(firstToolWindow.isVisible).isTrue()
    assertThat(secondToolWindow.isVisible).isFalse()
    assertThat(events).containsExactly(ToolWindowManagerEventType.HideToolWindow.name)
    assertThat(managers.owner.getToolWindow(ID)!!.isVisible).isTrue()
  }

  @Test
  fun `a show request of a frame guest selects it and shows the presenting tool window`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.register(first)
    managers.register(second)
    setActive(second)

    firstToolWindow.show()

    assertThat(managers.owner.getToolWindow(ID)!!.isVisible).isTrue()
    assertThat(managers.owner.presentation.selectedProjectOf(ID)).isSameAs(first)
    assertThat(firstToolWindow.isVisible).isTrue()
  }

  @Test
  fun `a hide of the presenting tool window hides the presented tool window`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.register(first)
    firstToolWindow.show()

    managers.owner.hideToolWindow(ID)

    assertThat(firstToolWindow.isVisible).isFalse()
  }

  @Test
  fun `a hide request hides the presenting tool window only for the selected project`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.register(first)
    val secondToolWindow = managers.register(second)
    firstToolWindow.show()

    secondToolWindow.hide()
    assertThat(managers.owner.getToolWindow(ID)!!.isVisible).describedAs("the tool window shows another project").isTrue()

    firstToolWindow.hide()
    assertThat(managers.owner.getToolWindow(ID)!!.isVisible).isFalse()
    assertThat(firstToolWindow.isVisible).isFalse()
  }

  @Test
  fun `the close of a frame guest removes its entry and selects another project`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.register(first)
    val secondToolWindow = managers.register(second)
    secondToolWindow.show()

    managers.of(second).projectClosing()

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(first)
    assertThat(managers.owner.presentation.selectedProjectOf(ID)).isSameAs(first)
    assertThat(firstToolWindow.isVisible).isTrue()
    assertThat(managers.owner.getToolWindow(ID)!!.isVisible).isTrue()
  }

  @Test
  fun `the presenting tool window leaves with the last entry and comes back with the next one`() = onEdt {
    val managers = createManagers()
    managers.register(first).show()
    val presenting = managers.owner.getEntry(ID)!!

    managers.of(first).doUnregisterToolWindow(ID)

    assertThat(presenting.toolWindow.isAvailable).isFalse()
    assertThat(presenting.toolWindow.isVisible).isFalse()
    assertThat(presenting.stripeButton).isNull()

    val secondToolWindow = managers.register(second)

    assertThat(presenting.toolWindow.isAvailable).isTrue()
    assertThat(presenting.toolWindow.isVisible).describedAs("the removal of the last entry hid the tool window").isTrue()
    assertThat(secondToolWindow.isVisible).isTrue()
  }

  @Test
  fun `a tool window that is not available has no entry`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.register(first)
    val secondToolWindow = managers.register(second)
    firstToolWindow.show()

    firstToolWindow.setAvailable(false)

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(second)
    assertThat(firstToolWindow.isVisible).isFalse()
    assertThat(secondToolWindow.isVisible).describedAs("the tool window is visible, so it selects another project").isTrue()

    firstToolWindow.setAvailable(true)

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(second, first)
    assertThat(managers.owner.presentation.selectedProjectOf(ID)).isSameAs(second)
  }

  @Test
  fun `a frame owner with content of its own is the first entry and stays selected`() = onEdt {
    val managers = createManagers()
    managers.owner.layoutState.addInfo(ID, visibleInfo())
    val ownToolWindow = managers.owner.registerToolWindow(task(), buttonManager(managers.owner)).toolWindow
    setActive(first)

    val firstToolWindow = managers.register(first)

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(owner, first)
    assertThat(managers.owner.presentation.selectedProjectOf(ID)).describedAs("the user sees the content of the owner").isSameAs(owner)
    assertThat(firstToolWindow.isVisible).isFalse()
    assertThat(managers.owner.presentation.shownToolWindowOf(ID)).isNull()

    managers.owner.presentation.select(ID, first)

    assertThat(firstToolWindow.isVisible).isTrue()
    assertThat(ownToolWindow.isVisible).isTrue()
    assertThat(managers.owner.presentation.shownToolWindowOf(ID)).isSameAs(firstToolWindow)
  }

  @Test
  fun `a tool window that presents every project itself takes no frame guest`() = onEdt {
    val managers = createManagers()
    val task = RegisterToolWindowTaskData(id = ID, anchor = ToolWindowAnchor.LEFT, contentFactory = MultiProjectFactory())
    managers.owner.registerToolWindow(task, buttonManager(managers.owner))

    val firstToolWindow = managers.register(first)
    firstToolWindow.show()

    assertThat(managers.owner.presentation.entriesOf(ID)).isEmpty()
    assertThat(firstToolWindow.isVisible).isFalse()
  }

  @Test
  fun `a presented tool window takes the place of the presenting tool window`() = onEdt {
    val managers = createManagers()
    managers.owner.layoutState.addInfo(ID, visibleInfo().also {
      it.anchor = ToolWindowAnchor.RIGHT
      it.isSplit = true
    })

    val firstToolWindow = managers.register(first)

    assertThat(firstToolWindow.isVisible).describedAs("the stored layout of the owner shows the tool window").isTrue()
    assertThat(firstToolWindow.anchor).isEqualTo(ToolWindowAnchor.RIGHT)
    assertThat(firstToolWindow.isSplitMode).isTrue()
  }

  @Test
  fun `the decorator of the presenting tool window holds the decorator of the shown tool window`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.register(first)
    val secondToolWindow = managers.register(second)
    firstToolWindow.show()
    val presentingDecorator = managers.owner.getEntry(ID)!!.toolWindow.decorator!!

    assertThat(SwingUtilities.isDescendingFrom(firstToolWindow.decorator, presentingDecorator)).isTrue()
    assertThat(firstToolWindow.paneDecorator).isSameAs(presentingDecorator)
    assertThat(InternalDecoratorImpl.findTopLevelDecorator(firstToolWindow.component))
      .describedAs("the two decorators belong to two tool windows")
      .isSameAs(firstToolWindow.decorator)
    assertThat(InternalDecoratorImpl.findTopLevelDecorator(firstToolWindow.decorator)).isNull()

    managers.owner.presentation.select(ID, second)

    assertThat(firstToolWindow.decorator!!.parent).describedAs("a hidden decorator is in no hierarchy").isNull()
    assertThat(SwingUtilities.isDescendingFrom(secondToolWindow.decorator, presentingDecorator)).isTrue()
  }

  @Test
  fun `the presenting tool window has the border around the project selector and the shown tool window`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.register(first)
    managers.register(second)
    firstToolWindow.show()
    val presentingDecorator = managers.owner.getEntry(ID)!!.toolWindow.decorator!!

    // a headless test has no theme customization, so the decorator has the border itself, not the island holder of a theme
    assertThat(presentingDecorator.border).isNotNull()
    assertThat(firstToolWindow.decorator!!.border).isNull()
  }

  @Test
  fun `the data context of a presented tool window has the project of the tool window`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.register(first)
    managers.register(second)
    setActive(second)
    firstToolWindow.show()

    assertThat(firstToolWindow.dataContextProject).isSameAs(first)
    assertThat(managers.owner.getEntry(ID)!!.toolWindow.dataContextProject)
      .describedAs("the presenting tool window shows no project of its own")
      .isNull()
  }

  @Test
  fun `a title action of a presented tool window acts on the manager of the frame owner`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.register(first)
    val context = DataContext { dataId -> if (PlatformDataKeys.TOOL_WINDOW.`is`(dataId)) firstToolWindow else null }
    val event = AnActionEvent.createEvent(context, null, ActionPlaces.TOOLWINDOW_TITLE, ActionUiKind.NONE, null)

    assertThat(getToolWindowManagerForEvent(event)).isSameAs(managers.owner)
  }

  @Test
  fun `a tool window that hides on empty content has an entry only with content`() = onEdt {
    val managers = createManagers()
    // as Find: the registration is not available, and the first content makes the tool window available
    val toolWindow = managers.of(first).registerToolWindow(ID) {
      anchor = ToolWindowAnchor.LEFT
      shouldBeAvailable = false
    } as ToolWindowImpl
    val presenting = managers.owner.getEntry(ID)!!
    assertThat(toolWindow.isPresented).isTrue()
    assertThat(presenting.toolWindow.isAvailable).isFalse()
    assertThat(managers.owner.presentation.entriesOf(ID)).isEmpty()

    val content = addContent(toolWindow)

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(first)
    assertThat(presenting.toolWindow.isAvailable).isTrue()
    assertThat(presenting.toolWindow.isVisible).describedAs("content does not show the tool window").isFalse()

    toolWindow.show()
    assertThat(toolWindow.isVisible).isTrue()

    toolWindow.contentManager.removeContent(content, true)

    assertThat(managers.owner.presentation.entriesOf(ID)).describedAs("the last content left").isEmpty()
    assertThat(toolWindow.isVisible).isFalse()
    assertThat(presenting.toolWindow.isVisible).isFalse()
    assertThat(presenting.toolWindow.isAvailable).isFalse()
    assertThat(presenting.stripeButton).isNull()

    addContent(toolWindow)

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(first)
    assertThat(presenting.toolWindow.isAvailable).isTrue()
    assertThat(presenting.stripeButton).isNotNull()
    assertThat(presenting.toolWindow.isVisible).describedAs("the tool window hid itself, so new content does not show it").isFalse()
  }

  @Test
  fun `the last content of the selected project hides the tool window and removes the entry of the project`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.registerFromCode(first)
    val secondToolWindow = managers.registerFromCode(second)
    val firstContent = addContent(firstToolWindow)
    addContent(secondToolWindow)
    firstToolWindow.show()

    firstToolWindow.contentManager.removeContent(firstContent, true)

    val presenting = managers.owner.getToolWindow(ID)!!
    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(second)
    assertThat(presenting.isVisible).describedAs("the tool window hides with its last content, as in a frame with one project").isFalse()
    assertThat(presenting.isAvailable).isTrue()

    managers.owner.showToolWindow(ID)

    assertThat(managers.owner.presentation.selectedProjectOf(ID)).isSameAs(second)
    assertThat(secondToolWindow.isVisible).isTrue()
    assertThat(firstToolWindow.isVisible).isFalse()
  }

  @Test
  fun `a show request brings an empty tool window back to the selector`() = onEdt {
    val managers = createManagers()
    val toolWindow = managers.registerFromCode(first)
    toolWindow.contentManager.removeContent(addContent(toolWindow), true)
    assertThat(managers.owner.presentation.entriesOf(ID)).isEmpty()

    // as the empty state action of Find and Run
    toolWindow.show()

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(first)
    assertThat(toolWindow.isVisible).isTrue()
  }

  @Test
  fun `a tool window that the frame owner registered from code presents the frame guests`() = onEdt {
    val managers = createManagers()
    val own = managers.owner.registerToolWindow(task(singleProject = false), buttonManager(managers.owner)).toolWindow

    val firstToolWindow = managers.register(first)

    assertThat(managers.owner.getToolWindow(ID)).isSameAs(own)
    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(owner, first)

    firstToolWindow.show()

    assertThat(managers.owner.presentation.shownToolWindowOf(ID)).isSameAs(firstToolWindow)
  }

  @Test
  fun `a frame owner without content of its own is no entry of the tool window that it registered from code`() = onEdt {
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
    val managers = createManagers()
    val ownTask = task(singleProject = false).copy(shouldBeAvailable = false)
    val own = managers.owner.registerToolWindow(ownTask, buttonManager(managers.owner)).toolWindow

    val firstToolWindow = managers.register(first)

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(first)
    assertThat(own.isAvailable).describedAs("the entry of the frame guest makes the tool window available").isTrue()

    managers.owner.showToolWindow(ID)

    assertThat(managers.owner.presentation.shownToolWindowOf(ID)).isSameAs(firstToolWindow)

    managers.of(first).doUnregisterToolWindow(ID)

    assertThat(own.isAvailable).describedAs("no frame guest has the tool window").isFalse()
  }

  @Test
  fun `the code of the frame owner gets the tool window that presents the frame guests`() = onEdt {
    val managers = createManagers()
    managers.registerFromCode(first)
    val presenting = managers.owner.getToolWindow(ID)

    // without it, a second registration of the id throws
    val registered = managers.owner.registerToolWindow(RegisterToolWindowTask(id = ID, anchor = ToolWindowAnchor.LEFT))

    assertThat(registered).isSameAs(presenting)
  }

  private fun addContent(toolWindow: ToolWindow): Content {
    val content = ContentFactory.getInstance().createContent(JPanel(), "content", false)
    toolWindow.contentManager.addContent(content)
    return content
  }

  private fun onEdt(test: suspend () -> Unit): Unit = timeoutRunBlocking {
    createdContent.clear()
    withContext(Dispatchers.EDT) {
      test()
    }
  }

  private fun setActive(project: Project) {
    WindowManagerEx.getInstanceEx().setActiveProject(project)
  }

  /** The names of the events that the manager of [project] publishes from now on. */
  private fun recordEvents(project: Project): List<String> {
    val events = ArrayList<String>()
    project.messageBus.connect(disposable).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun toolWindowShown(toolWindow: ToolWindow) {
        events.add("shown")
      }

      override fun stateChanged(toolWindowManager: ToolWindowManager, toolWindow: ToolWindow, changeType: ToolWindowManagerEventType) {
        events.add(changeType.name)
      }
    })
    return events
  }

  /** The managers of the frame owner and of its two frame guests. The record of the test window manager holds the guests. */
  private fun createManagers(): Managers {
    val windowManager = WindowManagerEx.getInstanceEx()
    val ownerManager = createManager(owner)
    val guestManagers = listOf(first, second).associateWith { guest ->
      windowManager.addFrameGuest(owner, guest)
      Disposer.register(disposable) { windowManager.removeFrameGuest(guest) }
      createManager(guest).also { it.presentThrough(ownerManager) }
    }
    return Managers(ownerManager, guestManagers)
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
      val actionId = ActivateToolWindowAction.Manager.getActionIdForToolWindow(ID)
      if (actionManager.getAction(actionId) != null) {
        actionManager.unregisterAction(actionId)
      }
    }
    return manager
  }

  private class Managers(@JvmField val owner: ToolWindowManagerImpl, private val guests: Map<Project, ToolWindowManagerImpl>) {
    fun of(guest: Project): ToolWindowManagerImpl = guests.getValue(guest)

    /** Registers the tool window of [guest], which the frame owner presents. */
    fun register(guest: Project): ToolWindowImpl {
      val manager = of(guest)
      val toolWindow = manager.registerToolWindow(task(), buttonManager(manager)).toolWindow
      assertThat(toolWindow.isPresented).isTrue()
      return toolWindow
    }

    /** Registers the tool window of [guest] as the code of Run does: no factory, and the tool window hides on empty content. */
    fun registerFromCode(guest: Project): ToolWindowImpl {
      val toolWindow = of(guest).registerToolWindow(RegisterToolWindowTask(id = ID, anchor = ToolWindowAnchor.LEFT)) as ToolWindowImpl
      assertThat(toolWindow.isPresented).isTrue()
      toolWindow.setToHideOnEmptyContent(true)
      return toolWindow
    }
  }
}

/** The name of each project that got the content of its tool window. A name, because a static field must not hold a project. */
private val createdContent = ArrayList<String>()

private fun buttonManager(manager: ToolWindowManagerImpl): ToolWindowButtonManager {
  return manager.getButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
}

private fun task(singleProject: Boolean = true): RegisterToolWindowTaskData {
  val contentFactory = object : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
      createdContent.add(project.name)
    }
  }
  return RegisterToolWindowTaskData(id = ID, anchor = ToolWindowAnchor.LEFT, contentFactory = contentFactory, singleProject = singleProject)
}

private class MultiProjectFactory : MultiProjectToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
  }
}

private fun visibleInfo(): WindowInfoImpl {
  return WindowInfoImpl().also {
    it.id = ID
    it.anchor = ToolWindowAnchor.LEFT
    it.isVisible = true
  }
}
