package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.impl.DriverCallException
import com.intellij.driver.sdk.Inlay
import com.intellij.driver.sdk.SimpleColoredText
import com.intellij.driver.sdk.getNotifications
import com.intellij.driver.sdk.go.ui.addToWatchesButton
import com.intellij.driver.sdk.go.ui.attachToProcessDialog
import com.intellij.driver.sdk.go.ui.breakpointBalloonDoneButton
import com.intellij.driver.sdk.go.ui.breakpointConditionEditor
import com.intellij.driver.sdk.go.ui.consoleEditors
import com.intellij.driver.sdk.go.ui.contextMenuGroup
import com.intellij.driver.sdk.go.ui.contextMenuItems
import com.intellij.driver.sdk.go.ui.evaluateField
import com.intellij.driver.sdk.go.ui.expressionEditors
import com.intellij.driver.sdk.go.ui.framesList
import com.intellij.driver.sdk.go.ui.gutterDebugMenuItem
import com.intellij.driver.sdk.go.ui.installGoToolLink
import com.intellij.driver.sdk.go.ui.muteBreakpointsButton
import com.intellij.driver.sdk.go.ui.runGutterIcon
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.invokeActionByShortcut
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditorForFile
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.mainToolbar
import com.intellij.driver.sdk.ui.components.common.toolwindows.DebugToolWindowUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.debugToolWindow
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.components.elements.waitSelected
import com.intellij.driver.sdk.ui.enabled
import com.intellij.driver.sdk.ui.present
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitNotNull
import com.intellij.driver.sdk.withRetries
import java.awt.event.KeyEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DebugActions(val driver: Driver, val ui: UiRobot) {

  fun evaluateExpression(text: String) {
    waitForDebugButtons()
    ui.editor(xQuery { byAccessibleName("Editor") }).click()
    ui.keyboard {
      typeText(text)
      hotKey(KeyEvent.VK_ENTER)
      hotKey(KeyEvent.VK_ENTER)
    }
  }

  /**
   * @param startTimeout how long to wait for the debug tool window, i.e. for the binary to be built and the debugger
   * to be launched. Builds on a remote target such as WSL are slower than local ones, especially the first one.
   * @param sessionTimeout how long to wait for the session to be ready to report variables.
   * @param fileName the file whose editor gives the context of the action. Without it, the focused component does.
   */
  fun debug(
    waitForDebugVariables: Boolean = true,
    startTimeout: Duration = 5.minutes,
    sessionTimeout: Duration = 1.minutes,
    fileName: String? = null,
  ) {
    if (fileName == null) {
      driver.invokeAction(DEBUG_ACTION)
    }
    else {
      driver.withContext {
        ideFrame { driver.invokeAction(DEBUG_ACTION, component = codeEditorForFile(fileName).component) }
      }
    }
    waitForDebugToolWindow(startTimeout)
    if (waitForDebugVariables) {
      waitForDebugButtons(sessionTimeout)
    }
  }

  fun debugFromGutter(
    line: Int,
    configurationName: String,
    startTimeout: Duration = 5.minutes,
    sessionTimeout: Duration = 1.minutes,
  ) {
    driver.invokeAction("ExpandAllRegions")
    driver.withContext {
      ui.ideFrame { runGutterIcon(line).click() }
      ui.gutterDebugMenuItem(configurationName)
        .shouldBe("Debug action for '$configurationName' is not present in the gutter popup", present, 30.seconds)
        .click()
    }
    waitForDebugToolWindow(startTimeout)
    waitForDebugButtons(sessionTimeout)
  }

  fun toggleBreakpoint(line: Int) {
    expandFoldingCode()
    ui.editor().goToPosition(line, 1)
    driver.invokeAction(TOGGLE_BREAKPOINT_ACTION)
  }

  /**
   * Toggles a line breakpoint on [line] of the editor of [fileName].
   *
   * A running debug session adds a fragment editor of its own, which makes [toggleBreakpoint] ambiguous.
   */
  fun toggleBreakpointInFile(fileName: String, line: Int) {
    step("Toggle a breakpoint on line $line of '$fileName'") {
      driver.withContext {
        ideFrame {
          codeEditorForFile(fileName).apply {
            driver.invokeAction("ExpandAllRegions", component = component)
            moveCaretToOffset(document.getLineStartOffset(line - 1))
            driver.invokeAction(TOGGLE_BREAKPOINT_ACTION, component = component)
          }
        }
      }
    }
  }

  fun expandFoldingCode() {
    driver.invokeAction("ExpandAllRegions", component = ui.editor().component)
  }

  fun checkVariables(list: List<String>) {
    ui.ideFrame().shouldBe("expected variables are not found") {
      val variables = ui.x { byClass("XDebuggerTree") }
      list.all { it in variables.getAllTexts().asString("") }
    }
  }

  /** Waits until the console of the Debug tool window holds [output]. */
  fun checkConsoleOutput(output: String) {
    ui.ideFrame().debugToolWindow().consoleTab.click()

    var console = ""
    var failure: Throwable? = null
    ui.ideFrame().should(
      message = "The debug console contains '$output'",
      timeout = 1.minutes,
      errorMessage = { "The debug console shows:\n$console" + (failure?.let { "\nThe console could not be read: $it" } ?: "") },
    ) {
      runCatching { consoleText() }
        .onSuccess { console = it; failure = null }
        .onFailure { console = ""; failure = it }
      console.contains(output)
    }
  }

  private fun consoleText(): String =
    ui.ideFrame().debugToolWindow().consoleEditors().list().joinToString("\n") { it.text }

  /** Clicks the "Step Into" button of the Debug tool window. */
  fun clickStepInto() {
    step("Click 'Step Into' in the Debug tool window") {
      clickDebugToolWindowButton("Step Into") { stepIntoButton }
    }
  }

  /** Clicks the "Resume Program" button of the Debug tool window. */
  fun clickResume() {
    step("Click 'Resume Program' in the Debug tool window") {
      clickDebugToolWindowButton("Resume Program") { resumeButton }
    }
  }

  /** Presses the shortcut of "Step Into". */
  fun stepInto() {
    step("Press the 'Step Into' shortcut") { pressShortcutOf(STEP_INTO_ACTION) }
  }

  /** Presses the shortcut of "Step Over". */
  fun stepOver() {
    step("Press the 'Step Over' shortcut") { pressShortcutOf(STEP_OVER_ACTION) }
  }

  /** Presses the shortcut of "Step Out". */
  fun stepOut() {
    step("Press the 'Step Out' shortcut") { pressShortcutOf(STEP_OUT_ACTION) }
  }

  /**
   * Runs the program to the line of [anchor], a fragment of the text of [fileName].
   *
   * The caret goes to [anchor] and the action runs on the editor without a mouse click.
   */
  fun runToCursor(fileName: String, anchor: String) {
    step("Run to Cursor on '$anchor' of '$fileName'") {
      driver.withContext {
        ideFrame {
          codeEditorForFile(fileName).apply {
            val offset = text.indexOf(anchor)
            check(offset >= 0) { "The editor of '$fileName' has no '$anchor'" }
            moveCaretToOffset(offset)
            driver.invokeAction(RUN_TO_CURSOR_ACTION, component = component)
          }
        }
      }
    }
  }

  /**
   * Waits until the top frame of the Frames list is in [function], and at [location] when it is given.
   *
   * [location] takes the `file.go:15` form of the Frames list.
   */
  fun shouldStopAt(function: String, location: String? = null) {
    val where = if (location == null) "'$function'" else "'$function' at $location"
    step("[check] The debugger stops in $where") {
      var topFrame: String? = null
      var frames = emptyList<String>()
      waitFor(
        message = "The top frame is in $where",
        timeout = STEP_TIMEOUT,
        errorMessage = { "The top frame is '$topFrame', the frames are $frames" },
      ) {
        frames = runCatching { framesList().items }.getOrDefault(emptyList())
        topFrame = topFrame()
        topFrame.isAt(function, location)
      }
    }
  }

  /** Steps over until the top frame is back in [function] at [location], and fails after [maxSteps] steps. */
  fun stepOverUntilStopsAt(function: String, location: String, maxSteps: Int = MAX_STEP_OVER_COUNT) {
    step("Step Over until the debugger returns to '$function' at $location") {
      var frame = topFrame()
      var steps = 0
      while (!frame.isAt(function, location)) {
        check(steps < maxSteps) { "The debugger is still in '$frame' after $maxSteps Step Over actions" }
        val previousFrame = frame
        stepOver()
        steps++
        frame = waitFor(
          message = "The top frame changes after Step Over #$steps",
          timeout = STEP_TIMEOUT,
          errorMessage = { "The top frame is still '$it'" },
          getter = { topFrame() },
          checker = { it != null && it != previousFrame && !it.contains(LOADING_FRAME) },
        )
      }
    }
  }

  /** Expands the node at [path] of the Variables view. A node matches an element of [path] when its text contains it. */
  fun expandVariable(vararg path: String) {
    val pathText = path.joinToString("/")
    step("Expand '$pathText' in the Variables view") {
      var failure: Throwable? = null
      waitFor(
        message = "'$pathText' is expanded",
        timeout = STEP_TIMEOUT,
        errorMessage = { "The Variables view has no '$pathText': ${failure?.message}" },
      ) {
        runCatching { driver.withContext { variablesTree().expandPath(*path, fullMatch = false) } }
          .onFailure { failure = it }
          .isSuccess
      }
    }
  }

  /**
   * The rows of the Variables view. A row is the path of its node, from the top-level variable down to the node.
   *
   * A node is its full presentation, so the row of `wd` reads `wd = {string} "/tmp"`.
   */
  fun variableRows(): List<List<String>> =
    driver.withContext { variablesTree().collectExpandedPaths().map { it.path } }

  /** Waits until [condition] holds for [variableRows]. */
  fun shouldShowVariables(message: String, condition: (List<List<String>>) -> Boolean) {
    step("[check] $message") {
      var rows = emptyList<List<String>>()
      var failure: Throwable? = null
      waitFor(
        message = message,
        timeout = STEP_TIMEOUT,
        errorMessage = {
          "The Variables view shows the rows:\n${rows.joinToString("\n") { it.joinToString(" > ") }}" +
          (failure?.let { "\nThe rows could not be read: $it" } ?: "")
        },
      ) {
        runCatching { variableRows() }
          .onSuccess { rows = it; failure = null }
          .onFailure { rows = emptyList(); failure = it }
        condition(rows)
      }
    }
  }

  /** Waits until the Variables view shows the top-level row of [name] with [value], for example `x = {int} 1`. */
  fun shouldShowVariable(name: String, value: String) {
    shouldShowVariables("'$name' is $value") { rows -> rows.any { it.isTopLevelRow(name) && it.single().endsWith(" $value") } }
  }

  /** Waits until the Variables view shows no top-level row of [name]. */
  fun shouldNotShowVariable(name: String) {
    shouldShowVariables("No row of '$name'") { rows -> rows.none { it.isTopLevelRow(name) } }
  }

  /** The tree reads a row as fragments joined with spaces, so `localInt * 2` comes back as `localInt  *  2`. */
  private fun List<String>.isTopLevelRow(name: String): Boolean =
    size == 1 && single().normalizeSpaces().startsWith("${name.normalizeSpaces()} = ")

  private fun String.normalizeSpaces(): String = replace(SPACES, " ")

  /** Toggles [option] from the context menu of the Variables view, for example "Show Types". */
  fun toggleVariablesViewOption(option: String) {
    step("Toggle '$option' from the context menu of the Variables view") {
      driver.withContext {
        focusVariablesTree()
        variablesTree().rightClickRow(0)
        ui.popupMenu().select(option)
      }
    }
  }

  /** Stops the debug session with the shortcut of "Stop", and waits for the debugger to report its exit code. */
  fun stopDebugSessionByShortcut() {
    step("Press the 'Stop' shortcut") {
      pressShortcutOf(STOP_ACTION)
      checkConsoleOutput(DEBUGGER_FINISHED_OUTPUT)
    }
  }

  /** Clicks [button] of the Debug tool window once it is enabled. The button is looked up on every poll. */
  private fun clickDebugToolWindowButton(name: String, button: DebugToolWindowUi.() -> UiComponent) {
    driver.withContext {
      ideFrame {
        debugToolWindow {
          var state = ""
          waitFor(
            message = "The '$name' button is enabled",
            timeout = STEP_TIMEOUT,
            errorMessage = { "The '$name' button is $state" },
          ) {
            runCatching { button().isEnabled() }
              .onSuccess { state = if (it) "enabled" else "disabled" }
              .onFailure { state = "absent: ${it.message}" }
              .getOrDefault(false)
          }
          button().click()
        }
      }
    }
  }

  /** Presses the first keyboard shortcut of [actionId] in the active keymap. */
  private fun pressShortcutOf(actionId: String) {
    driver.withContext { ui.ideFrame().invokeActionByShortcut(actionId) }
  }

  private fun framesList() = ui.ideFrame().debugToolWindow().framesList()

  private fun variablesTree() = ui.ideFrame().debugToolWindow().variableTree

  /**
   * Moves the focus to the Variables view.
   *
   * While the Frames list has the focus, it shows its truncated selected frame in full. That expanded frame covers the first
   * rows of the Variables view and takes the next click.
   */
  private fun focusVariablesTree() {
    variablesTree().setFocus()
  }

  /** The selected frame of the Frames list, or null while the list is absent or has no single selection. */
  private fun topFrame(): String? = runCatching { framesList().selectedItems.singleOrNull() }.getOrNull()

  private fun String?.isAt(function: String, location: String?): Boolean =
    this != null && !contains(LOADING_FRAME) && contains(function) && (location == null || contains("($location)"))

  /** Waits for the Debug tool window, i.e. for the binary to be built and the debugger to be launched. */
  private fun waitForDebugToolWindow(timeout: Duration) {
    waitFor(
      message = "The Debug tool window is shown",
      timeout = timeout,
      errorMessage = { "The Debug tool window is absent, the IDE shows the notifications ${notificationTitles()}" },
    ) { ui.ideFrame().debugToolWindow().present() }
  }

  private fun notificationTitles(): List<String?> =
    runCatching { driver.getNotifications().map { it.getTitle() } }.getOrDefault(emptyList())

  private fun waitForDebugButtons(timeout: Duration = 1.minutes) {
    var state = ""
    waitFor(
      message = "The debug session is ready",
      timeout = timeout,
      errorMessage = { "The debug session is not ready: $state" },
    ) {
      runCatching {
        val restartButtonPresent = ui.ideFrame().mainToolbar.restartDebugButton.present()
        val treePresent = ui.ideFrame().debugToolWindow().variableTree.present()
        if (!treePresent) {
          // A session tab reused from a previous session opens with the view that session showed last.
          ui.ideFrame().debugToolWindow().threadsAndVariablesTab.click()
        }
        val collecting = treePresent && ui.ideFrame().debugToolWindow().variableTree.getAllTexts().asString().contains("collecting")
        // A running session shows the tree with a single "Connected" node; only a suspended one selects a frame.
        val frame = topFrame()
        val suspended = frame != null && !frame.contains(LOADING_FRAME)
        state = "the restart button is ${if (restartButtonPresent) "present" else "absent"}, " +
                "the variables are ${if (!treePresent) "not shown" else if (collecting) "still collecting" else "collected"}, " +
                "the selected frame is '$frame'"
        restartButtonPresent && treePresent && !collecting && suspended
      }.getOrElse {
        state = "the lookup failed: ${it.message}"
        false
      }
    }
  }

  /** Opens the "Attach to Process" dialog. The IDE looks for `gops` while the dialog collects the processes. */
  fun openAttachToProcessDialog() {
    step("Open the 'Attach to Process' dialog") {
      driver.invokeAction(ATTACH_TO_PROCESS_ACTION)
      ui.attachToProcessDialog()
        .shouldBe("The 'Attach to Process' dialog is not opened", present, DIALOG_TIMEOUT)
    }
  }

  fun closeAttachToProcessDialog() {
    step("Close the 'Attach to Process' dialog") {
      ui.attachToProcessDialog().cancelButton.click()
      ui.attachToProcessDialog().waitNotFound(DIALOG_TIMEOUT)
    }
  }

  /**
   * Waits until the IDE reports that it cannot list the Go processes.
   *
   * See `go.debugger.cannot.retrieve.go.processes` and `go.notification.go.tool.is.missing.run.go.get.text` of
   * `GoBundle.properties`.
   */
  fun shouldReportMissingGops() {
    step("Verify the IDE reports the missing '$GOPS_EXECUTABLE' tool") {
      var titles = emptyList<String>()
      waitFor(
        message = "The IDE shows the '$MISSING_GOPS_TITLE' notification",
        timeout = NOTIFICATION_TIMEOUT,
        errorMessage = { "The IDE shows the notifications $titles" },
      ) {
        titles = driver.getNotifications().map { it.getTitle() }
        titles.contains(MISSING_GOPS_TITLE)
      }
      val notification = driver.getNotifications().first { it.getTitle() == MISSING_GOPS_TITLE }
      check(notification.getContent() == MISSING_GOPS_CONTENT) {
        "The notification does not name '$GOPS_EXECUTABLE' as the missing tool, but says '${notification.getContent()}'"
      }
    }
  }

  /** Runs `go install` for `gops` from the link of the notification which [shouldReportMissingGops] waits for. */
  fun installGopsFromNotification() {
    step("Install '$GOPS_EXECUTABLE' from the notification") {
      driver.withContext {
        ui.installGoToolLink(MISSING_GOPS_TITLE, INSTALL_GOPS_LINK).click()
      }
    }
  }

  /**
   * Selects the process the executable name of which holds [executableName] in the "Attach to Process" dialog, and
   * attaches the debugger to it.
   */
  fun attachToProcess(executableName: String) {
    step("Attach the debugger to the '$executableName' process") {
      driver.withContext {
        ui.attachToProcessDialog().apply {
          filterField.text = executableName

          var rows = emptyList<String>()
          waitFor(
            message = "The dialog lists the '$executableName' process",
            timeout = PROCESS_LIST_TIMEOUT,
            errorMessage = { "The dialog lists $rows" },
          ) {
            rows = processTable.rowsOf(executableName)
            rows.isNotEmpty()
          }

          processTable.clickCell(processTable.rowIndexOf(executableName), EXECUTABLE_COLUMN)
          attachButton
            .shouldBe("No debugger is available for the '$executableName' process", enabled, DIALOG_TIMEOUT)
            .click()
          waitNotFound(DIALOG_TIMEOUT)
        }
      }
    }
  }

  /** Waits until the debugger suspends the program it is attached to. */
  fun shouldSuspendAtBreakpoint() {
    step("Verify the debugger suspends the program") {
      driver.withContext {
        ideFrame {
          debugToolWindow {
            resumeButton.shouldBe("The debugger does not suspend the program", enabled, SUSPEND_TIMEOUT)
            variableTree.shouldBe("The frame of the suspended program shows no variables") {
              getAllTexts().isNotEmpty()
            }
          }
        }
      }
    }
  }

  /**
   * Stops the debug session from the "Stop" button of the Debug tool window.
   *
   * The tool window keeps the tab of the stopped session, so the end of the session is read from the console.
   */
  fun stopDebugSession() {
    step("Stop the debug session") {
      driver.withContext {
        ideFrame { debugToolWindow().stopButton.click() }
      }
      checkConsoleOutput(DEBUGGER_FINISHED_OUTPUT)
    }
  }

  /** Stops the debug session a previous test has left, when there is one. */
  fun stopDebugSessionIfRunning() {
    step("Stop the debug session of a previous test, when there is one") {
      val running = driver.withContext { runCatching { ui.ideFrame().mainToolbar.stopButton.present() }.getOrDefault(false) }
      if (running) {
        stopDebugSession()
      }
    }
  }

  fun removeAllBreakpoints() {
    step("Remove all the breakpoints") {
      driver.invokeAction(REMOVE_ALL_BREAKPOINTS_ACTION)
    }
  }

  /** Toggles "Mute Breakpoints" from the toolbar of the Debug tool window. */
  fun toggleMuteBreakpoints() {
    step("Toggle 'Mute Breakpoints' in the Debug tool window") {
      driver.withContext { ui.ideFrame().debugToolWindow().muteBreakpointsButton().click() }
    }
  }

  /** Clears "Mute Breakpoints" if a previous debug session has a toolbar. */
  fun unmuteBreakpoints() {
    step("Clear 'Mute Breakpoints' in the Debug tool window") {
      driver.withContext {
        val toolWindow = ui.ideFrame().debugToolWindow()
        if (!toolWindow.present()) return@withContext
        val button = toolWindow.muteBreakpointsButton()
        if (button.present()) {
          if (button.isSelected) button.click()
          button.waitSelected(false)
        }
      }
    }
  }

  /** Waits until the debugger reports [exitCode] as the exit code of the program in the console. */
  fun shouldFinishWithExitCode(exitCode: Int) {
    checkConsoleOutput(debuggerFinishedOutput(exitCode))
  }

  /** Waits until the debugger reports the end of the program in the console, whatever the exit code. */
  fun shouldFinish() {
    checkConsoleOutput(DEBUGGER_FINISHED_PREFIX)
  }

  /** Waits until the Frames list holds a frame in [function], and at [location] when it is given. */
  fun shouldHaveFrame(function: String, location: String? = null) {
    val where = if (location == null) "'$function'" else "'$function' at $location"
    step("[check] The Frames list holds a frame in $where") {
      var frames = emptyList<String>()
      waitFor(
        message = "A frame is in $where",
        timeout = STEP_TIMEOUT,
        errorMessage = { "The frames are $frames" },
      ) {
        frames = runCatching { framesList().items }.getOrDefault(emptyList())
        frames.any { it.isAt(function, location) }
      }
    }
  }

  /** Sets [condition] on the breakpoint of [line] of [fileName] from the breakpoint properties balloon. */
  fun setBreakpointCondition(fileName: String, line: Int, condition: String) {
    step("Set the condition '$condition' on the breakpoint of line $line of '$fileName'") {
      driver.withContext {
        ideFrame {
          codeEditorForFile(fileName).apply {
            moveCaretToOffset(document.getLineStartOffset(line - 1))
            driver.invokeAction(EDIT_BREAKPOINT_ACTION, component = component)
          }
        }
        ui.breakpointConditionEditor()
          .shouldBe("The breakpoint properties balloon is not shown", present, DIALOG_TIMEOUT)
        enterBreakpointCondition(condition)
        ui.breakpointBalloonDoneButton().click()
        ui.breakpointBalloonDoneButton().waitNotFound(DIALOG_TIMEOUT)
      }
    }
  }

  /**
   * Sets [condition] in the "Condition:" field of the open breakpoint properties balloon.
   *
   * The balloon replaces the editor of the field while it loads the expression. A text set into a replaced editor is lost.
   * So each poll finds the field again and sets the text when the field does not show it. The next poll checks the text.
   * A poll that fails during a replacement counts as a miss.
   */
  private fun enterBreakpointCondition(condition: String) {
    var shown = ""
    var failure: Throwable? = null
    waitFor(
      message = "The 'Condition:' field shows '$condition'",
      timeout = DIALOG_TIMEOUT,
      errorMessage = {
        "The 'Condition:' field shows '$shown'" + (failure?.let { "\nThe field could not be read or set: $it" } ?: "")
      },
    ) {
      runCatching {
        val field = ui.breakpointConditionEditor()
        shown = field.text
        if (shown != condition) field.text = condition
        shown == condition
      }
        .onSuccess { failure = null }
        .onFailure { failure = it }
        .getOrDefault(false)
    }
  }

  /** Picks [renderer] in the "View as" submenu of the context menu of the top-level variable [name]. */
  fun viewVariableAs(name: String, renderer: String) {
    step("View '$name' as '$renderer' from the context menu of the Variables view") {
      driver.withContext {
        invokeVariableMenuItem(name, renderer, submenu = VIEW_AS_MENU)
        closePopupMenu()
      }
    }
  }

  /**
   * Sets [value] on the top-level variable [name] from "Set Value" of the context menu of the Variables view.
   *
   * A frame update of the session cancels an open inplace editor, so the attempt is repeated when the row keeps its value.
   */
  fun setVariableValue(name: String, value: String) {
    step("Set '$name' to '$value' from the context menu of the Variables view") {
      driver.withContext {
        withRetries(message = "'$name' is set to '$value'", times = MENU_ATTEMPTS, onError = { ui.keyboard { escape() } }) {
          invokeVariableMenuItem(name, SET_VALUE_ITEM)
          focusedExpressionEditor().apply {
            text = value
            keyboard { enter() }
          }
          var rows = emptyList<List<String>>()
          waitFor(
            message = "The row of '$name' shows '$value'",
            timeout = MENU_TIMEOUT,
            errorMessage = { "The Variables view shows the rows ${rows.map { it.joinToString(" > ") }}" },
          ) {
            rows = runCatching { variableRows() }.getOrDefault(emptyList())
            rows.any { it.isTopLevelRow(name) && it.single().endsWith(" $value") }
          }
        }
      }
    }
  }

  /** Adds the watch [expression] from the inline evaluate field of the Variables view. */
  fun addWatch(expression: String) {
    step("Add the watch '$expression' from the evaluate field of the Variables view") {
      driver.withContext {
        ideFrame {
          debugToolWindow {
            evaluateField().apply {
              click()
              text = expression
            }
            addToWatchesButton()
              .shouldBe("The 'Add to Watches' button is disabled", enabled, DIALOG_TIMEOUT)
              .click()
          }
        }
      }
    }
  }

  /** Removes the watch [expression] from its context menu in the Variables view. */
  fun removeWatch(expression: String) {
    step("Remove the watch '$expression' from the context menu of the Variables view") {
      driver.withContext { invokeVariableMenuItem(expression, REMOVE_WATCH_ITEM) }
    }
  }

  /** The inline debugger values the editor of [fileName] shows after its lines. */
  fun inlineValues(fileName: String): List<InlineValue> = driver.withContext {
    ui.ideFrame().codeEditorForFile(fileName).run {
      editor.getInlayModel().getAfterLineEndElementsInRange(0, text.length).mapNotNull { inlay ->
        inlineValueOf(inlay, document.getLineNumber(inlay.getOffset()) + 1)
      }
    }
  }

  /** Waits until the editor of [fileName] shows every one of [expected]: the variable, and the value it holds. */
  fun shouldShowInlineValues(fileName: String, expected: Map<String, String>) {
    val expectedText = expected.entries.joinToString { "${it.key}: ${it.value}" }
    step("[check] The editor of '$fileName' shows the inline values $expectedText") {
      var values = emptyList<InlineValue>()
      var failure: Throwable? = null
      waitFor(
        message = "The editor shows the inline values $expectedText",
        timeout = STEP_TIMEOUT,
        errorMessage = {
          "The editor shows the inline values $values" + (failure?.let { "\nThe inline values could not be read: $it" } ?: "")
        },
      ) {
        runCatching { inlineValues(fileName) }
          .onSuccess { values = it; failure = null }
          .onFailure { values = emptyList(); failure = it }
        expected.all { (name, value) -> values.any { it.name == name && it.value == value } }
      }
    }
  }

  /** An inline debugger value: the [name] of the variable, its [value], and the [line] of the editor it is shown on. */
  data class InlineValue(val line: Int, val name: String, val value: String)

  /** The value of [inlay], or null for an inlay of another kind, such as a Code Vision hint. */
  private fun inlineValueOf(inlay: Inlay, line: Int): InlineValue? {
    val texts = try {
      driver.cast(inlay.getRenderer(), InlineDebugRendererRef::class).getPresentation()?.getTexts()
    }
    catch (_: DriverCallException) {
      return null
    }
    if (texts.isNullOrEmpty()) return null
    return InlineValue(line, texts.first(), texts.drop(1).joinToString(""))
  }

  /** Clicks [item] in the context menu of the top-level variable [name], or in its [submenu]. A closed menu is reopened. */
  private fun invokeVariableMenuItem(name: String, item: String, submenu: String? = null) {
    withRetries(message = "'$item' is clicked in the context menu of '$name'", times = MENU_ATTEMPTS, onError = ::closePopupMenu) {
      rightClickVariable(name)
      if (submenu != null) {
        waitForMenuEntry(name, submenu) { ui.contextMenuGroup(submenu).present() }
        ui.contextMenuGroup(submenu).click()
      }
      waitForMenuEntry(name, item) { ui.contextMenuItems(item).list().isNotEmpty() }
      ui.contextMenuItems(item).list().first().click()
    }
  }

  private fun waitForMenuEntry(name: String, entry: String, present: () -> Boolean) {
    waitFor(
      message = "The context menu of '$name' shows '$entry'",
      timeout = MENU_TIMEOUT,
      errorMessage = { "The context menu of '$name' shows ${popupMenuItems()}, without '$entry'" },
      condition = present,
    )
  }

  private fun popupMenuItems(): List<String> = runCatching { ui.popupMenu().itemsList() }.getOrDefault(emptyList())

  private fun closePopupMenu() {
    waitFor(message = "The debugger context menu is closed", timeout = MENU_TIMEOUT) {
      if (ui.contextMenuItems().list().isNotEmpty() || ui.popupMenu().present()) {
        ui.keyboard { escape() }
        false
      }
      else {
        true
      }
    }
  }

  private fun rightClickVariable(name: String) {
    focusVariablesTree()
    variablesTree().rightClickRow { it.normalizeSpaces().startsWith("${name.normalizeSpaces()} = ") }
  }

  /** The expression editor which has the focus. */
  private fun focusedExpressionEditor(): JEditorUiComponent =
    waitNotNull(message = "An expression editor has the focus", timeout = DIALOG_TIMEOUT) {
      ui.expressionEditors().list().firstOrNull { it.isFocusOwner() }
    }

  /** The rows which hold [executableName] in one of their cells, joined cell by cell. */
  private fun JTableUiComponent.rowsOf(executableName: String): List<String> =
    content().values
      .map { cells -> cells.values.joinToString(" ") }
      .filter { it.contains(executableName) }

  /** The index of the first row which holds [executableName] in one of its cells. */
  private fun JTableUiComponent.rowIndexOf(executableName: String): Int =
    content().entries
      .firstOrNull { (_, cells) -> cells.values.any { it.contains(executableName) } }
      ?.key
    ?: error("The dialog lists no process of '$executableName'")

  /** The renderer of an inline debugger value. */
  @Remote("com.intellij.xdebugger.impl.inline.InlineDebugRenderer", plugin = "com.intellij/intellij.platform.debugger.impl.ui")
  private interface InlineDebugRendererRef {
    fun getPresentation(): SimpleColoredText?
  }

  private companion object {
    private val SPACES = Regex("\\s+")
    private const val ATTACH_TO_PROCESS_ACTION = "XDebugger.AttachToProcess"
    private const val DEBUG_ACTION = "DebugClass"
    private const val TOGGLE_BREAKPOINT_ACTION = "ToggleLineBreakpoint"
    private const val EDIT_BREAKPOINT_ACTION = "EditBreakpoint"
    private const val REMOVE_ALL_BREAKPOINTS_ACTION = "Debugger.RemoveAllBreakpoints"
    private const val STEP_INTO_ACTION = "StepInto"
    private const val STEP_OVER_ACTION = "StepOver"
    private const val STEP_OUT_ACTION = "StepOut"
    private const val RUN_TO_CURSOR_ACTION = "RunToCursor"
    private const val VIEW_AS_MENU = "View as"
    private const val SET_VALUE_ITEM = "Set Value\u2026"
    private const val REMOVE_WATCH_ITEM = "Remove Watch"
    private const val STOP_ACTION = "Stop"

    /** The text of the frame the Frames list shows while it computes the frames. */
    private const val LOADING_FRAME = "Loading"
    private const val MAX_STEP_OVER_COUNT = 10
    private val STEP_TIMEOUT = 30.seconds
    private val MENU_TIMEOUT = 10.seconds
    private const val MENU_ATTEMPTS = 3

    private const val GOPS_EXECUTABLE = "gops"
    private const val MISSING_GOPS_TITLE = "Cannot retrieve Go processes"
    private const val MISSING_GOPS_CONTENT = "Cannot find 'gops' in GOPATH."

    /** See `go.notification.go.tool.is.missing.run.go.get.invoke.go.get.action.text` of `GoBundle.properties`. */
    private const val INSTALL_GOPS_LINK = "go get gops"

    /** The first column of the process table of the dialog. It holds the name of the executable. */
    private const val EXECUTABLE_COLUMN = 0

    private val DIALOG_TIMEOUT = 1.minutes
    private val NOTIFICATION_TIMEOUT = 2.minutes
    private val PROCESS_LIST_TIMEOUT = 2.minutes
    private const val DEBUGGER_FINISHED_PREFIX = "Debugger finished with the exit code"
    private val DEBUGGER_FINISHED_OUTPUT = debuggerFinishedOutput(0)

    private fun debuggerFinishedOutput(exitCode: Int) = "$DEBUGGER_FINISHED_PREFIX $exitCode"

    private val SUSPEND_TIMEOUT = 2.minutes
  }
}
