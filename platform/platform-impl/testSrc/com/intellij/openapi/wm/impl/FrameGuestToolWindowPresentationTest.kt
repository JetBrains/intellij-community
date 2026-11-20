// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.ToolWindowEmptyStateAction
import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.MultiProjectToolWindowFactory
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.RegisterToolWindowTaskData
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowBalloonShowOptions
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameProjectOrderProvider
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowPaneOldButtonManager
import com.intellij.toolWindow.ToolWindowProjectSelectorTabs
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ref.GCWatcher
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.function.Supplier
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

private const val ID = "FrameGuestPresentationTestToolWindow"
private const val LEGACY_TOOL_WINDOWS_KEY = "ide.frame.guests.legacy.tool.windows"

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
    assertThat(secondToolWindow.decorator!!.parent).describedAs("the decorator of the guest left the frame").isNull()
    assertThat(managers.owner.presentation.selectedProjectOf(ID)).describedAs("a later event selects another project").isNull()

    UIUtil.dispatchAllInvocationEvents()

    assertThat(managers.owner.presentation.selectedProjectOf(ID)).isSameAs(first)
    assertThat(firstToolWindow.isVisible).isTrue()
    assertThat(managers.owner.getToolWindow(ID)!!.isVisible).isTrue()
  }

  @Test
  fun `the close of a frame creates no content for a project that closes next`() = onEdt {
    val managers = createManagers()
    managers.register(first)
    managers.register(second).show()
    assertThat(createdContent).containsExactly(second.name)

    // as the close of the frame owner and as an exit: every project of the frame closes in one event
    managers.of(second).projectClosing()
    managers.of(first).projectClosing()
    UIUtil.dispatchAllInvocationEvents()

    assertThat(createdContent).describedAs("the tool window of the first project was never shown").containsExactly(second.name)
    assertThat(managers.owner.getToolWindow(ID)).isNull()
  }

  @Test
  fun `the disposal of a presented tool window removes its entry`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.register(first)
    val secondToolWindow = managers.register(second)
    secondToolWindow.show()

    // the frame guest goes away without the close event
    Disposer.dispose(managers.of(second).getEntry(ID)!!.disposable)
    UIUtil.dispatchAllInvocationEvents()

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(first)
    assertThat(managers.owner.presentation.selectedProjectOf(ID)).isSameAs(first)
    assertThat(secondToolWindow.presentedBy).isNull()
    assertThat(firstToolWindow.isVisible).isTrue()
  }

  @Test
  fun `a frame guest that closes gives no tool window to the frame owner`() = onEdt {
    val managers = createManagers()
    val guestManager = managers.of(first)
    guestManager.projectClosing()

    val toolWindow = guestManager.registerToolWindow(task(), buttonManager(guestManager)).toolWindow

    assertThat(toolWindow.presentedBy).isNull()
    assertThat(managers.owner.getToolWindow(ID)).describedAs("the frame owner presents no tool window of a closed frame guest").isNull()
  }

  @Test
  fun `the tool window that presents only frame guests leaves with the last one and comes back with the next one`() = onEdt {
    val managers = createManagers()
    managers.register(first).show()

    managers.of(first).doUnregisterToolWindow(ID)

    assertThat(managers.owner.getEntry(ID)).describedAs("the tool window has no content of the frame owner").isNull()

    val secondToolWindow = managers.register(second)

    val presenting = managers.owner.getEntry(ID)!!
    assertThat(presenting.toolWindow.isAvailable).isTrue()
    assertThat(presenting.stripeButton).isNotNull()
    assertThat(presenting.toolWindow.isVisible).describedAs("the layout of the frame owner keeps the visible state").isTrue()
    assertThat(secondToolWindow.isVisible).isTrue()
  }

  @Test
  fun `the tool window that presents only frame guests leaves its activation action when it leaves`() = onEdt {
    val managers = createManagers()
    val declared = declareActivationAction()
    managers.register(first)

    managers.of(first).doUnregisterToolWindow(ID)

    assertThat(managers.owner.getEntry(ID)).isNull()
    assertThat(ActionManager.getInstance().getAction(activationActionId())).describedAs("the action is one for the application").isSameAs(declared)
  }

  @Test
  fun `the tool window of the frame owner keeps the activation action of the tool window that a frame guest registered first`() = onEdt {
    val managers = createManagers()
    val declared = declareActivationAction()
    val placeholder = managers.register(first).presentedBy

    val own = managers.owner.registerToolWindow(task(), buttonManager(managers.owner)).toolWindow

    assertThat(own).isNotSameAs(placeholder)
    assertThat(ActionManager.getInstance().getAction(activationActionId())).isSameAs(declared)
  }

  @Test
  fun `the tool window that presents only frame guests has the title of a frame guest that has the id`() = onEdt {
    val managers = createManagers()
    var titleRequests = 0
    val firstTitle = Supplier {
      titleRequests++
      "first title"
    }
    managers.of(first).registerToolWindow(RegisterToolWindowTask(id = ID, anchor = ToolWindowAnchor.LEFT, stripeTitle = firstTitle))
    managers.of(second).registerToolWindow(RegisterToolWindowTask(id = ID, anchor = ToolWindowAnchor.LEFT, stripeTitle = Supplier { "second title" }))
    val presenting = managers.owner.getToolWindow(ID) as ToolWindowImpl
    assertThat(presenting.stripeTitle).isEqualTo("first title")

    managers.of(first).doUnregisterToolWindow(ID)
    val requestsAfterDetach = titleRequests

    assertThat(presenting.stripeTitle).isEqualTo("second title")
    assertThat(titleRequests).describedAs("the frame owner holds no supplier of a frame guest that left").isEqualTo(requestsAfterDetach)
  }

  @Test
  fun `the tool window that presents only frame guests holds no registration of a frame guest`() = onEdt {
    val managers = createManagers()

    val registrationTitle = registerAndReplaceTitle(managers.of(first))

    assertThat(managers.owner.getToolWindow(ID)!!.stripeTitle).isEqualTo("second title")
    // the supplier of a registration can hold a dialog and its project
    registrationTitle.ensureCollected()
  }

  /**
   * Registers the tool window of a frame guest with a title supplier, and then gives the tool window another supplier.
   * So the tool window of the frame guest does not hold the first supplier after the call, and no variable of the test does.
   *
   * @return the watcher of the first supplier
   */
  private fun registerAndReplaceTitle(manager: ToolWindowManagerImpl): GCWatcher {
    // an object: a lambda that captures nothing is one instance for the whole run
    val title = object : Supplier<String> {
      override fun get(): String = "first title"
    }
    val toolWindow = manager.registerToolWindow(RegisterToolWindowTask(id = ID, anchor = ToolWindowAnchor.LEFT, stripeTitle = title))
    assertThat((toolWindow as ToolWindowImpl).isPresented).isTrue()
    toolWindow.setStripeTitleProvider { "second title" }
    return GCWatcher.tracking(title)
  }

  @Test
  fun `the tool window with content of the frame owner stays when its last frame guest leaves`() = onEdt {
    val managers = createManagers()
    managers.of(first).registerToolWindow(RegisterToolWindowTask(id = ID, anchor = ToolWindowAnchor.LEFT, stripeTitle = Supplier { "first title" }))
    val presenting = managers.owner.getToolWindow(ID) as ToolWindowImpl
    assertThat(presenting.stripeTitle).isEqualTo("first title")
    addContent(presenting)

    managers.of(first).doUnregisterToolWindow(ID)

    assertThat(managers.owner.getToolWindow(ID)).isSameAs(presenting)
    assertThat(presenting.isAvailable).isTrue()
    assertThat(presenting.stripeTitle).isEqualTo("first title")
    assertThat(managers.owner.presentation.entriesOf(ID)).isEmpty()
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

    assertThat(managers.owner.presentation.entriesOf(ID))
      .describedAs("the entry comes back to its place in the order of the frame")
      .containsExactly(first, second)
    assertThat(managers.owner.presentation.selectedProjectOf(ID)).isSameAs(second)
  }

  @Test
  fun `the entries follow the order that the frame gives its projects`() = onEdt {
    // as a workspace that lists its projects in an order of its own: the frame guests joined as first, second
    var order = listOf(second, owner, first)
    ExtensionTestUtil.maskExtensions(
      ProjectFrameProjectOrderProvider.EP_NAME,
      listOf(object : ProjectFrameProjectOrderProvider {
        override fun getProjectOrder(frameOwner: Project): List<Project>? = if (frameOwner === owner) order else null
      }),
      disposable,
    )
    val managers = createManagers()
    managers.owner.registerToolWindow(task(), buttonManager(managers.owner))
    managers.register(first)
    managers.register(second).show()

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(second, owner, first)
    assertThat(IdeFrameGuests.orderedProjectsOf(owner)).containsExactly(second, owner, first)

    // the order changes, and the next update of the tool window asks for it
    order = listOf(first)
    managers.owner.presentation.select(ID, first)

    assertThat(managers.owner.presentation.entriesOf(ID)).describedAs("a project that the order lacks goes last").containsExactly(first, owner, second)
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
    val own = managers.owner.registerToolWindow(task, buttonManager(managers.owner)).toolWindow

    val firstToolWindow = managers.register(first)
    firstToolWindow.show()

    assertThat(managers.owner.presentation.entriesOf(ID)).isEmpty()
    assertThat(firstToolWindow.isVisible).isFalse()
    assertThat(firstToolWindow.presentedBy).isNull()

    firstToolWindow.setAnchor(ToolWindowAnchor.RIGHT, null)

    assertThat(own.anchor).describedAs("a tool window that the frame owner does not present moves no tool window").isEqualTo(ToolWindowAnchor.LEFT)
  }

  @Test
  fun `a frame guest that registers a tool window of the whole frame gets the tool window of the frame owner`() = onEdt {
    val managers = createManagers()
    val factory = MultiProjectFactory()

    // as the code that registers one tool window in every open project
    val registered = managers.of(first).registerToolWindow(ID) {
      anchor = ToolWindowAnchor.LEFT
      contentFactory = factory
    }

    val own = managers.owner.getToolWindow(ID) as ToolWindowImpl
    assertThat(registered).isSameAs(own)
    assertThat(own.isMultiProject).isTrue()
    assertThat(own.isPresented).isFalse()
    assertThat(managers.of(first).getToolWindow(ID)).describedAs("the frame guest has no tool window of its own").isNull()
    assertThat(managers.owner.presentation.entriesOf(ID)).isEmpty()

    val registeredBySecond = managers.of(second).registerToolWindow(ID) {
      anchor = ToolWindowAnchor.LEFT
      contentFactory = factory
    }

    assertThat(registeredBySecond).isSameAs(own)
  }

  @Test
  fun `a frame guest gets the tool window of the whole frame that the frame owner has`() = onEdt {
    val managers = createManagers()
    val task = RegisterToolWindowTaskData(id = ID, anchor = ToolWindowAnchor.LEFT, contentFactory = MultiProjectFactory())
    val own = managers.owner.registerToolWindow(task, buttonManager(managers.owner)).toolWindow

    // the code of the frame guest does not know the factory of the frame owner
    val registered = managers.of(first).registerToolWindow(RegisterToolWindowTask(id = ID, anchor = ToolWindowAnchor.LEFT))

    assertThat(registered).isSameAs(own)
    assertThat(managers.of(first).getToolWindow(ID)).isNull()
  }

  @Test
  fun `a presented tool window has its presenting tool window from the attach to the detach`() = onEdt {
    val managers = createManagers()
    val guestManager = managers.of(first)
    val task = task()
    val entry = guestManager.registerToolWindow(
      preparedTask = guestManager.prepareRegisterToolWindowTask(task),
      buttonManager = buttonManager(guestManager),
      layout = guestManager.getLayout(),
      ensureToolWindowActionRegistered = true,
    ).entry
    val toolWindow = entry.toolWindow
    assertThat(toolWindow.isPresented).isTrue()
    assertThat(toolWindow.presentedBy).describedAs("the frame owner does not present the tool window yet").isNull()

    guestManager.attachPresented(entry, task)

    assertThat(toolWindow.presentedBy).isSameAs(managers.owner.getToolWindow(ID))
    assertThat(toolWindow.decoratorInPane).isSameAs(managers.owner.getEntry(ID)!!.toolWindow.decorator)

    guestManager.doUnregisterToolWindow(ID)

    assertThat(toolWindow.presentedBy).isNull()
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
    assertThat(firstToolWindow.decoratorInPane).isSameAs(presentingDecorator)
    assertThat(InternalDecoratorImpl.findTopLevelDecorator(firstToolWindow.component))
      .describedAs("the two decorators belong to two tool windows")
      .isSameAs(firstToolWindow.decorator)
    assertThat(InternalDecoratorImpl.findTopLevelDecorator(firstToolWindow.decorator)).isNull()

    managers.owner.presentation.select(ID, second)

    assertThat(firstToolWindow.decorator!!.parent).describedAs("a hidden decorator is in no hierarchy").isNull()
    assertThat(SwingUtilities.isDescendingFrom(secondToolWindow.decorator, presentingDecorator)).isTrue()
  }

  @Test
  fun `the project selector stays when the content of the frame owner is split`() = onEdt {
    val managers = createManagers()
    val own = managers.owner.registerToolWindow(task(), buttonManager(managers.owner)).toolWindow
    addContent(own)
    val splitContent = addContent(own)
    managers.owner.showToolWindow(ID)
    managers.register(first)
    val decorator = own.decorator!!
    assertThat(selectorIn(decorator)).describedAs("the frame owner and a frame guest have an entry").isNotNull()

    decorator.splitWithContent(splitContent, SwingConstants.RIGHT, -1)

    assertThat(decorator.mode.isSplit).isTrue()
    assertThat(selectorIn(decorator)).describedAs("the user can still select the frame guest").isNotNull()

    managers.owner.presentation.select(ID, first)
    assertThat(selectorIn(decorator)).isNotNull()

    managers.owner.presentation.select(ID, owner)
    assertThat(decorator.mode.isSplit).isTrue()
    assertThat(selectorIn(decorator)).isNotNull()

    managers.of(first).doUnregisterToolWindow(ID)
    assertThat(selectorIn(decorator)).describedAs("one project needs no selector").isNull()
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
  @RegistryKey(key = LEGACY_TOOL_WINDOWS_KEY, value = "true")
  fun `the data context of a tool window of the frame owner has the frame owner`() = onEdt {
    val managers = createManagers()
    // as Find and Run: the code of the frame owner registers the tool window, without a factory
    val own = managers.owner.registerToolWindow(RegisterToolWindowTaskData(id = ID, anchor = ToolWindowAnchor.LEFT), buttonManager(managers.owner)).toolWindow
    managers.register(first)
    setActive(first)

    assertThat(own.dataContextProject).describedAs("the tool window shows the content of the frame owner").isSameAs(owner)
  }

  @Test
  @RegistryKey(key = LEGACY_TOOL_WINDOWS_KEY, value = "true")
  fun `the data context of a tool window with the marker has the active project`() = onEdt {
    val managers = createManagers()
    val task = RegisterToolWindowTaskData(id = ID, anchor = ToolWindowAnchor.LEFT, contentFactory = MultiProjectFactory())
    val own = managers.owner.registerToolWindow(task, buttonManager(managers.owner)).toolWindow
    setActive(first)

    assertThat(own.dataContextProject).describedAs("the data context keeps the active project").isNull()
  }

  @Test
  @RegistryKey(key = LEGACY_TOOL_WINDOWS_KEY, value = "true")
  fun `the data context of a tool window of a frame owner without content of its own has the active project`() = onEdt {
    suppressOwnContentOfOwner()
    val managers = createManagers()
    val own = managers.owner.registerToolWindow(RegisterToolWindowTaskData(id = ID, anchor = ToolWindowAnchor.LEFT), buttonManager(managers.owner)).toolWindow
    setActive(first)

    assertThat(own.dataContextProject).describedAs("the frame owner shows only its frame guests").isNull()
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
  fun `the activation action of a tool window without content shows the empty state of the active frame guest`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.registerFromCode(first)
    val content = addContent(firstToolWindow)
    firstToolWindow.show()
    // the frame guest closes its last result: the tool window hides, and no project has an entry
    firstToolWindow.contentManager.removeContent(content, true)
    assertThat(managers.owner.getToolWindow(ID)!!.isAvailable).isFalse()
    setActive(first)

    emptyStateAction().actionPerformed(activationEvent(first))

    assertThat(managers.owner.presentation.entriesOf(ID)).describedAs("the empty state is for the active project").containsExactly(first)
    assertThat(managers.owner.getToolWindow(ID)!!.isVisible).isTrue()
    assertThat(managers.owner.presentation.shownToolWindowOf(ID)).isSameAs(firstToolWindow)
  }

  @Test
  @RegistryKey(key = LEGACY_TOOL_WINDOWS_KEY, value = "true")
  fun `the activation action shows no empty state of a frame owner without content of its own`() = onEdt {
    suppressOwnContentOfOwner()
    val managers = createManagers()
    val firstToolWindow = managers.registerFromCode(first)
    firstToolWindow.contentManager.removeContent(addContent(firstToolWindow), true)
    setActive(owner)

    emptyStateAction().actionPerformed(activationEvent(owner))

    val presenting = managers.owner.getToolWindow(ID)!!
    assertThat(presenting.isAvailable).isFalse()
    assertThat(presenting.isVisible).isFalse()
  }

  @Test
  @RegistryKey(key = LEGACY_TOOL_WINDOWS_KEY, value = "true")
  fun `the activation action shows the empty state of a tool window of the whole frame in the frame owner`() = onEdt {
    suppressOwnContentOfOwner()
    val managers = createManagers()
    // as Commit in a workspace without a VCS mapping: only the frame owner has the tool window, and it is not available
    val task = RegisterToolWindowTaskData(
      id = ID,
      anchor = ToolWindowAnchor.LEFT,
      shouldBeAvailable = false,
      contentFactory = MultiProjectFactory(),
    )
    val own = managers.owner.registerToolWindow(task, buttonManager(managers.owner)).toolWindow
    setActive(first)

    emptyStateAction().actionPerformed(activationEvent(first))

    assertThat(managers.of(first).getToolWindow(ID)).describedAs("the frame guest has no tool window of the whole frame").isNull()
    assertThat(own.isAvailable).isTrue()
    assertThat(own.isVisible).isTrue()
  }

  @Test
  fun `a notification of a presented tool window goes to the tool window of the frame owner`() = onEdt {
    val managers = createManagers()
    // as Find without a result: the tool window of the frame guest has no content, so no project has an entry
    val firstToolWindow = managers.registerFromCode(first)
    firstToolWindow.contentManager.removeContent(addContent(firstToolWindow), true)

    managers.of(first).notifyByBalloon(ToolWindowBalloonShowOptions(toolWindowId = ID, type = MessageType.INFO, htmlBody = "No results"))

    assertThat(shownBalloons).describedAs("the pane of a frame guest is in no frame").containsExactly(owner.name)
  }

  @Test
  @RegistryKey(key = "run.toolwindow.merge.debug", value = "true")
  fun `a debugger notification of a frame guest goes to the Run tool window of the frame owner`() = onEdt {
    val managers = createManagers()
    val actionManager = ActionManager.getInstance()
    val runActionId = ActivateToolWindowAction.Manager.getActionIdForToolWindow(ToolWindowId.RUN)
    if (actionManager.getAction(runActionId) == null) {
      // no plugin of the test declares the action, so the registration of the tool window creates it
      Disposer.register(disposable) { actionManager.unregisterAction(runActionId) }
    }
    // as JetBrains Light: the Run tool window shows the debugger, and no project has a Debug tool window
    val run = managers.of(first).registerToolWindow(RegisterToolWindowTask(id = ToolWindowId.RUN, anchor = ToolWindowAnchor.BOTTOM))
    assertThat((run as ToolWindowImpl).isPresented).isTrue()

    managers.of(first).notifyByBalloon(ToolWindowBalloonShowOptions(toolWindowId = ToolWindowId.DEBUG, type = MessageType.INFO, htmlBody = "Paused"))

    assertThat(shownBalloons).describedAs("the frame owner has the Run tool window that the user sees").containsExactly(owner.name)
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
    val own = managers.owner.registerToolWindow(task(), buttonManager(managers.owner)).toolWindow

    val firstToolWindow = managers.register(first)

    assertThat(managers.owner.getToolWindow(ID)).isSameAs(own)
    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(owner, first)

    firstToolWindow.show()

    assertThat(managers.owner.presentation.shownToolWindowOf(ID)).isSameAs(firstToolWindow)
  }

  @Test
  fun `a frame owner without content of its own is no entry of the tool window that it registered from code`() = onEdt {
    suppressOwnContentOfOwner()
    val managers = createManagers()
    val ownTask = task().copy(shouldBeAvailable = false)
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
  fun `a frame guest makes an empty tool window of the frame owner available`() = onEdt {
    val managers = createManagers()
    // as Find: the frame owner registered the tool window, and it has no content
    val own = managers.registerOwnerFromCode(shouldBeAvailable = false)
    assertThat(own.isAvailable).isFalse()

    val firstToolWindow = managers.registerFromCode(first)
    addContent(firstToolWindow)

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(first)
    assertThat(own.isAvailable).describedAs("a frame guest has content").isTrue()
    assertThat(managers.owner.getEntry(ID)!!.stripeButton).isNotNull()

    firstToolWindow.show()

    assertThat(own.isVisible).isTrue()
    assertThat(managers.owner.presentation.shownToolWindowOf(ID)).isSameAs(firstToolWindow)
  }

  @Test
  fun `the frame owner gets an entry with its content and loses it with its last content`() = onEdt {
    val managers = createManagers()
    val own = managers.registerOwnerFromCode(shouldBeAvailable = false)
    val firstToolWindow = managers.registerFromCode(first)
    addContent(firstToolWindow)
    firstToolWindow.show()

    val ownContent = addContent(own)

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(owner, first)
    assertThat(managers.owner.presentation.selectedProjectOf(ID)).describedAs("content selects no project").isSameAs(first)

    // as the code of Find after a search
    own.activate(null)

    assertThat(managers.owner.presentation.selectedProjectOf(ID)).isSameAs(owner)
    assertThat(firstToolWindow.isVisible).isFalse()

    own.contentManager.removeContent(ownContent, true)

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(first)
    assertThat(own.isAvailable).isTrue()
    assertThat(managers.owner.getEntry(ID)!!.stripeButton).describedAs("the frame guest keeps the stripe button").isNotNull()
    assertThat(own.isVisible).describedAs("the tool window hides with the last content of the project that it shows").isFalse()

    managers.owner.showToolWindow(ID)

    assertThat(managers.owner.presentation.shownToolWindowOf(ID)).isSameAs(firstToolWindow)
  }

  @Test
  fun `the last content of the frame owner keeps the tool window that shows a frame guest`() = onEdt {
    val managers = createManagers()
    val own = managers.registerOwnerFromCode(shouldBeAvailable = true)
    val ownContent = addContent(own)
    val firstToolWindow = managers.registerFromCode(first)
    addContent(firstToolWindow)
    firstToolWindow.show()

    own.contentManager.removeContent(ownContent, true)

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(first)
    assertThat(own.isVisible).isTrue()
    assertThat(firstToolWindow.isVisible).isTrue()
    assertThat(managers.owner.getEntry(ID)!!.stripeButton).isNotNull()

    firstToolWindow.contentManager.removeAllContents(true)

    assertThat(managers.owner.presentation.entriesOf(ID)).isEmpty()
    assertThat(own.isVisible).isFalse()
    assertThat(managers.owner.getEntry(ID)!!.stripeButton).describedAs("no project has content").isNull()
  }

  @Test
  fun `the content of the frame owner in a tool window that a frame guest registered first gives the owner an entry`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.registerFromCode(first)
    addContent(firstToolWindow)
    firstToolWindow.show()
    // as Find and Run: the code of the frame owner takes the tool window that exists
    val presenting = managers.owner.getToolWindow(ID) as ToolWindowImpl
    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(first)

    val ownContent = addContent(presenting)

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(owner, first)

    presenting.show()

    assertThat(managers.owner.presentation.selectedProjectOf(ID)).isSameAs(owner)
    assertThat(managers.owner.presentation.shownToolWindowOf(ID)).describedAs("the tool window shows the content of the frame owner").isNull()

    firstToolWindow.contentManager.removeAllContents(true)

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(owner)
    assertThat(presenting.isAvailable).isTrue()
    assertThat(presenting.isVisible).isTrue()

    presenting.contentManager.removeContent(ownContent, true)

    assertThat(managers.owner.presentation.entriesOf(ID)).isEmpty()
    assertThat(presenting.isVisible).isFalse()
    assertThat(managers.owner.getEntry(ID)!!.stripeButton).isNull()
  }

  @Test
  fun `the frame owner cannot make a tool window with an entry of a frame guest unavailable`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.registerFromCode(first)
    addContent(firstToolWindow)
    firstToolWindow.show()
    val presenting = managers.owner.getToolWindow(ID) as ToolWindowImpl

    // the code of the frame owner hides its empty tool window
    ContentManagerWatcher.watchContentManager(presenting, presenting.contentManager)

    assertThat(presenting.isAvailable).isTrue()
    assertThat(firstToolWindow.isVisible).isTrue()

    addContent(presenting)

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(owner, first)

    presenting.contentManager.removeAllContents(true)

    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(first)
    assertThat(presenting.isAvailable).isTrue()
  }

  @Test
  fun `the tool window of the frame owner takes the place of the tool window that a frame guest registered first`() = onEdt {
    val managers = createManagers()
    val firstToolWindow = managers.registerFromCode(first)
    addContent(firstToolWindow)
    firstToolWindow.show()
    val placeholder = managers.owner.getToolWindow(ID)

    // as a plugin that loads in both projects: without the takeover, the second registration of the id throws
    val own = managers.owner.registerToolWindow(task(), buttonManager(managers.owner)).toolWindow

    assertThat(own).isNotSameAs(placeholder)
    assertThat(managers.owner.getToolWindow(ID)).isSameAs(own)
    assertThat(firstToolWindow.presentedBy).isSameAs(own)
    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(owner, first)
    assertThat(own.isVisible).describedAs("the tool window before it was visible").isTrue()
    assertThat(managers.owner.getEntry(ID)!!.stripeButton).isNotNull()

    managers.owner.presentation.select(ID, first)

    assertThat(firstToolWindow.isVisible).isTrue()
    assertThat(SwingUtilities.isDescendingFrom(firstToolWindow.decorator, own.decorator)).isTrue()
  }

  @Test
  fun `the frame owner keeps the tool window of the frame guests when it has content of the owner`() = onEdt {
    val managers = createManagers()
    managers.registerFromCode(first)
    val placeholder = managers.owner.getToolWindow(ID) as ToolWindowImpl
    addContent(placeholder)

    val registered = managers.owner.registerToolWindow(task(), buttonManager(managers.owner)).toolWindow

    assertThat(registered).describedAs("a new tool window would lose the content").isSameAs(placeholder)
  }

  @Test
  fun `the frame guests get a new tool window when the frame owner unregisters its tool window`() = onEdt {
    val managers = createManagers()
    val own = managers.owner.registerToolWindow(task(), buttonManager(managers.owner)).toolWindow
    val firstToolWindow = managers.register(first)
    firstToolWindow.show()
    assertThat(firstToolWindow.isVisible).isTrue()

    managers.owner.doUnregisterToolWindow(ID)

    val presenting = managers.owner.getToolWindow(ID)
    assertThat(presenting).isNotNull().isNotSameAs(own)
    assertThat(firstToolWindow.presentedBy).isSameAs(presenting)
    assertThat(firstToolWindow.decorator!!.parent).describedAs("the decorator left the tool window that is gone").isNotSameAs(own.decorator)
    assertThat(managers.owner.presentation.entriesOf(ID)).containsExactly(first)

    firstToolWindow.show()

    assertThat(presenting!!.isVisible).isTrue()
    assertThat(firstToolWindow.isVisible).isTrue()
  }

  @Test
  fun `a tool window of the frame owner without frame guests leaves no tool window`() = onEdt {
    val managers = createManagers()
    managers.owner.registerToolWindow(task(), buttonManager(managers.owner))
    managers.register(first)
    managers.of(first).doUnregisterToolWindow(ID)

    managers.owner.doUnregisterToolWindow(ID)

    assertThat(managers.owner.getToolWindow(ID)).isNull()
  }

  /** Makes [owner] a frame owner without tool window content of its own, as the host of a JetBrains Light workspace. */
  private fun suppressOwnContentOfOwner() {
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
  }

  /** The activation action of the test tool window, with an empty state as the action of Find has. */
  private fun emptyStateAction(): ToolWindowEmptyStateAction {
    return object : ToolWindowEmptyStateAction(ID, null) {
      override fun setupEmptyText(project: Project, text: StatusText) {
      }

      override fun ensureToolWindowCreated(project: Project) {
      }
    }
  }

  /** Registers the activation action of the test tool window before the tool window, as a plugin that declares the action does. */
  private fun declareActivationAction(): AnAction {
    val action = emptyStateAction()
    ActionManager.getInstance().registerAction(activationActionId(), action)
    return action
  }

  /** The event of an activation action that the user starts while [project] is the project of the data context. */
  private fun activationEvent(project: Project): AnActionEvent {
    val context = DataContext { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null }
    return AnActionEvent.createEvent(context, null, ActionPlaces.MAIN_MENU, ActionUiKind.NONE, null)
  }

  /** The tabs of the project selector in the hierarchy of [decorator], or `null` when the decorator shows no selector. */
  private fun selectorIn(decorator: InternalDecoratorImpl): ToolWindowProjectSelectorTabs? {
    return UIUtil.findComponentOfType(decorator, ToolWindowProjectSelectorTabs::class.java)
  }

  private fun addContent(toolWindow: ToolWindow): Content {
    val content = ContentFactory.getInstance().createContent(JPanel(), "content", false)
    toolWindow.contentManager.addContent(content)
    return content
  }

  private fun onEdt(test: suspend () -> Unit): Unit = timeoutRunBlocking {
    createdContent.clear()
    shownBalloons.clear()
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
      createManager(guest).also { it.initAsFrameGuest(ownerManager.presentation) }
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

      override fun notifyByBalloon(options: ToolWindowBalloonShowOptions) {
        if (frameOwnerPresentation == null) {
          // a headless manager has no pane for a balloon, so the test records the request of a project with a frame
          shownBalloons.add(project.name)
        }
        else {
          super.notifyByBalloon(options)
        }
      }
    }
    // an action finds the manager of a project through the service
    project.replaceService(ToolWindowManager::class.java, manager, disposable)
    createdManagers.add(manager)
    Disposer.register(disposable) {
      val actionManager = ActionManager.getInstance()
      if (actionManager.getAction(activationActionId()) != null) {
        actionManager.unregisterAction(activationActionId())
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

    /** Registers the tool window of the frame owner as the code of Find and Run does: no factory, and the tool window hides on empty content. */
    fun registerOwnerFromCode(shouldBeAvailable: Boolean): ToolWindowImpl {
      val task = RegisterToolWindowTaskData(id = ID, anchor = ToolWindowAnchor.LEFT, shouldBeAvailable = shouldBeAvailable, hideOnEmptyContent = true)
      return owner.registerToolWindow(task, buttonManager(owner)).toolWindow
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

/** The name of each project whose manager got a request to show a balloon in its frame. */
private val shownBalloons = ArrayList<String>()

private fun activationActionId(): String = ActivateToolWindowAction.Manager.getActionIdForToolWindow(ID)

private fun buttonManager(manager: ToolWindowManagerImpl): ToolWindowButtonManager {
  return manager.getButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
}

private fun task(): RegisterToolWindowTaskData {
  val contentFactory = object : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
      createdContent.add(project.name)
    }
  }
  return RegisterToolWindowTaskData(id = ID, anchor = ToolWindowAnchor.LEFT, contentFactory = contentFactory)
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
