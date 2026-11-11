package com.intellij.driver.sdk.ui.components.common

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.ManualWaitForIndicators
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.isProjectOpened
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitNotNull
import com.intellij.driver.sdk.ui.DEFAULT_FIND_TIMEOUT
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.SwingUtilities
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.editor.EditorTabsManager
import com.intellij.driver.sdk.ui.components.common.dialogs.ideStatusBar
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowLeftToolbarUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowRightToolbarUi
import com.intellij.driver.sdk.ui.components.elements.WindowUiComponent
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.Window
import com.intellij.driver.sdk.ui.isIdeFocused
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.driver.sdk.withoutPauseOnIndicators
import com.intellij.openapi.diagnostic.logger
import com.intellij.tools.ide.util.common.logOutput
import org.jetbrains.annotations.ApiStatus
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

/** Waits until the frame is found and ready, and then runs [action] on it. */
fun Finder.ideFrame(action: IdeaFrameUI.() -> Unit) {
  ideFrame().waitFound().action()
}

/** Returns the frame without a search. A check such as `ideFrame().present()` can then find no frame. */
fun Driver.ideFrame(): IdeaFrameUI = ui.ideFrame()

/** Waits until the frame is found and ready, and then runs [action] on it. */
fun Driver.ideFrame(action: IdeaFrameUI.() -> Unit = {}): IdeaFrameUI = ui.ideFrame().waitFound().apply(action)

fun Driver.ideFrame(index: Int, action: IdeaFrameUI.() -> Unit = {}): IdeaFrameUI = ui.ideFrames().list()[index].apply(action)

/**
 * Brings the first project frame to the front after the IDE start, as [IdeaFrameUI.ensureFocused] does.
 * A reused IDE also calls it after it reopens the project for the next test.
 *
 * Returns false when no frame is shown yet or the frame is minimized. When another window of the IDE has the focus,
 * as a popup or a dialog does, and the IDE gets the keys, the call does not click.
 * The call does not wait for a frame or for the indicators, and it writes what it did to the build log.
 */
@ApiStatus.Internal
fun Driver.requestInitialFocus(): Boolean = withoutPauseOnIndicators {
  if (getOpenProjects().isEmpty()) return@withoutPauseOnIndicators false
  val frame = ui.ideFrames().list().firstOrNull() ?: return@withoutPauseOnIndicators false
  if (frame.isMinimized()) return@withoutPauseOnIndicators false
  val frameFocused = frame.isFocused()
  val outcome = when {
    frameFocused && frame.getsKeysOrUnknown() -> "the IDE frame has the focus"
    !frameFocused && isIdeFocused() && frame.getsKeysOrUnknown() -> "another window of the IDE has the focus and gets the keys"
    else -> {
      frame.toFront()
      "the IDE frame came to the front"
    }
  }
  logOutput("The initial focus request is done: $outcome")
  true
}

/**
 * Brings the last shown window of the IDE to the front, when no project is open and the IDE has no focus.
 * Such a window is a modal dialog or the Welcome screen. Returns true when the call clicked.
 *
 * The call clicks the middle of the native window title. A window without a native title, as a popup or a window
 * with a custom header, gets no click. The call does not wait for the indicators.
 */
@ApiStatus.Internal
fun Driver.bringStartWindowToFront(): Boolean = withoutPauseOnIndicators {
  if (isIdeFocused() || getOpenProjects().isNotEmpty()) return@withoutPauseOnIndicators false
  val window = utility(Window::class).getWindows().lastOrNull { it.isShowing() && titleHeight(it) > 0 }
               ?: return@withoutPauseOnIndicators false
  val location = window.getLocationOnScreen()
  val title = Point(location.x + window.width / 2, location.y + titleHeight(window) / 2)
  withContext(OnDispatcher.EDT) { window.toFront() }
  ui.robot.click(title, RemoteMouseButton.LEFT, 1)
  logOutput("No project is open, so the initial focus request clicked the title of a window of the IDE")
  true
}

/** The height of the native title of [window], or 0 when the window has no such title. */
private fun Driver.titleHeight(window: Window): Int {
  val rootPane = utility(SwingUtilities::class).getRootPane(window) ?: return 0
  return rootPane.getLocationOnScreen().y - window.getLocationOnScreen().y
}

/** An IDE built before [com.intellij.driver.sdk.ui.remote.Robot.hasInputFocus] fails the check, so it then gives true. */
private fun IdeaFrameUI.getsKeysOrUnknown(): Boolean {
  try {
    return robot.hasInputFocus()
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    logger<Driver>().info("The input focus check is not available: ${e.message}")
    return true
  }
}

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
    if (isWelcomeScreenProject(currentProject)) {
      logger<Driver>().info("The IDE frame shows the welcome-screen project, skipping the project frame checks")
      return
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

  /**
   * Returns true when [project] is the welcome-screen project.
   */
  internal fun isWelcomeScreenProject(project: Project): Boolean =
    runCatching {
      driver.utility(WelcomeUtils::class).isWelcomeProject(project)
    }.onFailure {
      logger<Driver>().info("The frame capabilities of the project are not available: ${it.message}")
    }.getOrDefault(false)

  @ManualWaitForIndicators
  fun waitForIndicators(timeout: Duration = 5.minutes) {
    driver.waitForIndicators(::project, timeout)
  }

  @ManualWaitForIndicators
  @ApiStatus.Internal
  @Deprecated("The Driver brings the frame to the front once after the IDE start. A test calls waitForIndicators only.",
              ReplaceWith("waitForIndicators(timeout)"))
  @OptIn(ManualEnsureFocused::class)
  fun waitForIndicatorsAndEnsureFocused(timeout: Duration = 5.minutes) {
    waitForIndicators(timeout)
    ensureFocused()
  }

  /**
   * Brings the frame to the front when the frame is not focused or the IDE does not get the keys.
   *
   * The Driver calls it once after the IDE start, because on Windows the IDE can start without the focus.
   * A reused IDE also calls it after it reopens the project for the next test.
   * Do not call it at the start of a test, also when the tests share one IDE. Call it only in these cases:
   * - the test switches between several windows of the IDE;
   * - an action opened a dialog that did not take the focus.
   */
  @ManualEnsureFocused
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

  /** Opens the settings of the project of this frame. The context is the frame, so the call does not depend on the focus. */
  fun openSettingsDialog() {
    step("Open settings dialog via IDE action") {
      driver.invokeAction("ShowSettings", now = false, component = component)
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

@RequiresOptIn(
  level = RequiresOptIn.Level.WARNING,
  message = "The Driver brings the IDE frame to the front once after the IDE start. " +
            "Call ensureFocused only when the test switches between windows of the IDE, " +
            "or when an action opened a dialog that did not take the focus. " +
            "Add @OptIn(ManualEnsureFocused::class) after you check this requirement."
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class ManualEnsureFocused

@Remote("com.intellij.ide.welcomeScreen.WelcomeUtils")
interface WelcomeUtils {
  fun isWelcomeProject(project: Project): Boolean
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
