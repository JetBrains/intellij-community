package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ManualWaitForIndicators
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.isProjectOpened
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitNotNull
import com.intellij.driver.sdk.ui.DEFAULT_FIND_TIMEOUT
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.editor.EditorTabsManager
import com.intellij.driver.sdk.ui.components.common.dialogs.ideStatusBar
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowLeftToolbarUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowRightToolbarUi
import com.intellij.driver.sdk.ui.components.elements.WindowUiComponent
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.Window
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.openapi.diagnostic.logger
import java.awt.Frame
import java.awt.Point
import javax.swing.JFrame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun Finder.ideFrame(): IdeaFrameUI = x(IdeaFrameUI::class.java) { byClass("IdeFrameImpl") }

fun Finder.currentIdeFrame(): IdeaFrameUI = ideFrames().list().let { frames ->
  when (frames.size) {
    0 -> throw IllegalStateException("No IDE frames found")
    1 -> frames[0]
    else -> frames.firstOrNull { it.isFocused() } ?: throw IllegalStateException("No focused IDE frame found")
  }
}

fun Finder.ideFrames(): UIComponentsList<IdeaFrameUI> = xx(IdeaFrameUI::class.java) { byClass("IdeFrameImpl") }

fun Finder.ideFrame(action: IdeaFrameUI.() -> Unit) {
  ideFrame().action()
}

fun Driver.ideFrame(action: IdeaFrameUI.() -> Unit = {}): IdeaFrameUI = ui.ideFrame().apply(action)

fun Driver.ideFrame(index: Int, action: IdeaFrameUI.() -> Unit = {}): IdeaFrameUI = ui.ideFrames().list()[index].apply(action)

/**
 * Waits until the IDE shows a window that a test can use.
 *
 * The IDE shows either project frames or the Welcome screen. This method waits for one of the two.
 * It then waits until each project frame is ready. See [IdeaFrameUI.waitReady].
 * The Welcome screen has no project, so the method does no readiness check on it.
 */
fun Driver.waitForIdeFrameReady(timeout: Duration = DEFAULT_FIND_TIMEOUT) {
  waitNotNull(
    message = "A project frame or the Welcome screen is available",
    timeout = maxOf(timeout, 1.minutes),
    getter = { ui.ideFrames().list().takeIf { it.isNotEmpty() || ui.welcomeScreen().present() } },
  ).forEach { it.waitReady(timeout) }
}

fun Finder.projectIdeFrame(projectName: String, action: IdeaFrameUI.() -> Unit) {
  x("//div[@class='IdeFrameImpl' and contains(@accessiblename, '${projectName}')]", IdeaFrameUI::class.java).action()
}

fun Driver.projectIdeFrame(projectName: String, action: IdeaFrameUI.() -> Unit) {
  this.ui.projectIdeFrame(projectName, action)
}

open class IdeaFrameUI(data: ComponentData) : WindowUiComponent(data) {
  private val ideaFrameComponent by lazy { driver.cast(component, IdeFrameImpl::class) }

  val project: Project?
    get() = driver.utility(ProjectFrameHelper::class).getFrameHelper(component).getProject()

  val editorTabsManager: EditorTabsManager get() = EditorTabsManager(this)

  val isFullScreen: Boolean
    get() = ideaFrameComponent.isInFullScreen()

  val isMaximized: Boolean
    get() = ideaFrameComponent.getExtendedState().and(JFrame.MAXIMIZED_BOTH) != 0

  val leftToolWindowToolbar: ToolWindowLeftToolbarUi =
    x(ToolWindowLeftToolbarUi::class.java) { byClass("ToolWindowLeftToolbar") }

  val rightToolWindowToolbar: ToolWindowRightToolbarUi =
    x(ToolWindowRightToolbarUi::class.java) { byClass("ToolWindowRightToolbar") }

  override fun waitReady(timeout: Duration) {
    val readinessTimeout = maxOf(timeout, 1.minutes)
    // The checks below search inside this frame. The caller caches the frame component before this call,
    // so these searches reuse the cached component and do not re-enter waitReady.
    super.waitReady(readinessTimeout)
    if (welcomeScreen().present()) {
      // In split mode the Welcome screen is a panel inside the frame. Such a frame has no project.
      logger<Driver>().info("The IDE frame shows the Welcome screen, skipping the project readiness checks")
      return
    }
    // Every other IdeFrameImpl belongs to a project window. In a monolith the Welcome screen is a separate
    // FlatWelcomeFrame. A null project therefore means that the project is still opening, so wait for it.
    val currentProject = waitNotNull(
      message = "The project of the IDE frame is available",
      timeout = readinessTimeout,
      getter = { runCatching { project }.getOrNull() },
    )
    waitFor("Project is opened", readinessTimeout) {
      driver.isProjectOpened(currentProject)
    }

    // The list holds only the frame chrome that every product shows. A tool-window stripe and a stripe button are
    // layout state, not frame readiness: `hideToolStripes` removes the stripe, and a stripe button needs a product
    // that registers that tool window. A test that needs one waits for it itself.
    // A required element is a group of alternatives, and the frame is ready when each group has a member present.
    // The toolbar needs a group: a custom header holds it, a frame that keeps the native window title holds it
    // directly, and a compact header hides it but keeps the header itself.
    val requiredComponents = listOf(
      listOf(mainToolbar, toolbarHeader),
      listOf(abstractToolbarCombo {
        and(byType("com.intellij.openapi.wm.impl.AbstractToolbarCombo"), contains(byAccessibleName(currentProject.getName())))
      }),
      listOf(ideStatusBar()),
    )
    fun List<List<UiComponent>>.describe(): String = joinToString { group -> group.joinToString(" or ") }
    waitFor(
      message = "IDE frame elements are rendered",
      timeout = readinessTimeout,
      errorMessage = { notRendered: List<List<UiComponent>> -> "IDE frame elements were not rendered: ${notRendered.describe()}" },
      getter = {
        requiredComponents.filterNot { group ->
          group.any { component -> runCatching { component.present() }.getOrDefault(false) }
        }.also { notRendered ->
          if (notRendered.isNotEmpty()) {
            logger<Driver>().info("Waiting for IDE frame elements to render: ${notRendered.describe()}")
          }
        }
      },
      checker = { it.isEmpty() },
    )
  }

  @ManualWaitForIndicators
  fun waitForIndicators(timeout: Duration = 5.minutes) {
    driver.waitForIndicators(::project, timeout)
  }

  @ManualWaitForIndicators
  fun waitForIndicatorsAndEnsureFocused(timeout: Duration = 5.minutes) {
    waitForIndicators(timeout)
    ensureFocused()
  }

  fun ensureFocused() {
    if (!isFocused() || !robot.hasInputFocus()) {
      toFront()
    }
  }

  fun closeProject() {
    step("Close project window and wait for it to disappear") {
      driver.invokeAction("CloseProject")
    }
  }

  fun saveAll() {
    step(name = "Save All files") {
      driver.invokeAction("SaveAll")
    }
  }

  fun maximize() {
    driver.withContext(OnDispatcher.EDT) {
      ideaFrameComponent.setExtendedState(ideaFrameComponent.getExtendedState().or(JFrame.MAXIMIZED_BOTH))
    }
  }

  fun unmaximize() {
    driver.withContext(OnDispatcher.EDT) {
      ideaFrameComponent.setExtendedState(ideaFrameComponent.getExtendedState() and JFrame.MAXIMIZED_BOTH.inv())
    }
  }

  fun resize(width: Int, height: Int) {
    driver.withContext(OnDispatcher.EDT) {
      ideaFrameComponent.setSize(width, height)
    }
  }

  fun openSettingsDialog() {
    step("Open settings dialog via IDE action") {
      driver.invokeAction("ShowSettings", now = false)
    }
  }

  override fun toFront() {
    super.toFront()
    // Click empty toolbar space to raise and focus the frame, falling back to the toolbar center when no suitable gap can be found
    val freePoint = if (mainToolbar.present()) mainToolbar.emptyAreaPointOrNull() else null
    if (freePoint != null) {
      mainToolbar.click(freePoint)
    }
    else {
      click(Point(component.width / 2, 0))
    }
  }

  fun isMinimized(): Boolean = ideaFrameComponent.getState() == Frame.ICONIFIED

  fun unminimize() {
    ideaFrameComponent.setState(Frame.NORMAL)
  }
}

@Remote("com.intellij.openapi.wm.impl.ProjectFrameHelper")
interface ProjectFrameHelper {
  fun getFrameHelper(window: Component): ProjectFrameHelper
  fun getProject(): Project?
}

@Remote("com.intellij.openapi.wm.impl.IdeFrameImpl")
interface IdeFrameImpl : Window {
  fun isInFullScreen(): Boolean
  fun getExtendedState(): Int
  fun setExtendedState(state: Int)
  fun setSize(width: Int, height: Int)
  fun getState(): Int
  fun setState(state: Int)
}
