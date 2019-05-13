// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testGuiFramework.fixtures.*
import com.intellij.testGuiFramework.fixtures.extended.ExtendedTableFixture
import com.intellij.testGuiFramework.fixtures.extended.RowFixture
import com.intellij.testGuiFramework.fixtures.newProjectWizard.NewProjectWizardFixture
import com.intellij.testGuiFramework.framework.GuiTestLocalRunner
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.GuiTestPaths.testScreenshotDirPath
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.framework.toPrintable
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.tryWithPause
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.typeMatcher
import com.intellij.testGuiFramework.launcher.system.SystemInfo
import com.intellij.testGuiFramework.launcher.system.SystemInfo.isMac
import com.intellij.testGuiFramework.util.*
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.AbstractComponentFixture
import org.fest.swing.fixture.JListFixture
import org.fest.swing.fixture.JTableFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.awt.Component
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.text.JTextComponent

/**
 * The main base class that should be extended for writing GUI tests.
 *
 * GuiTestCase contains methods of TestCase class like setUp() and tearDown() but also has a set of methods allows to use Kotlin DSL for
 * writing GUI tests (starts from comment KOTLIN DSL FOR GUI TESTING). The main concept of this DSL is using contexts of the current
 * component. Kotlin language gives us an opportunity to omit fixture instances to perform their methods, therefore code looks simpler and
 * more clear. Just use contexts functions to find appropriate fixtures and to operate with them:
 *
 * {@code <code>
 * welcomeFrame {     // <- context of WelcomeFrameFixture
 *   createNewProject()
 *   dialog("New Project Wizard") {
 *   // context of DialogFixture of dialog with title "New Project Wizard"
 *   }
 * }
 * </code>}
 *
 * All fixtures (or DSL methods for theese fixtures) has a timeout to find component on screen and equals to #defaultTimeout. To check existence
 * of specific component by its fixture use exists lambda function with receiver.
 *
 * The more descriptive documentation about entire framework could be found in the root of testGuiFramework (HowToUseFramework.md)
 *
 */
@RunWith(GuiTestLocalRunner::class)
open class GuiTestCase {

  @Rule
  @JvmField
  val screenshotsDuringTest = ScreenshotsDuringTest(500) // 0.5 sec

  @Rule
  @JvmField
  val guiTestRule = GuiTestRule()

  val projectsFolder: File = guiTestRule.projectsFolder

  val settingsTitle: String = if (isMac()) "Preferences" else "Settings"
  //  val defaultSettingsTitle: String = if (isMac()) "Default Preferences" else "Default Settings"
  val defaultSettingsTitle: String = if (isMac()) "Preferences for New Projects" else "Settings for New Projects"
  val slash: String = File.separator

  private val screenshotTaker: ScreenshotTaker = ScreenshotTaker()

  @Rule
  @JvmField
  val testMethod = TestName()

  @Rule
  @JvmField
  val logActionsDuringTest = LogActionsDuringTest()

  val projectFolder: String by lazy {
    val dir = File(projectsFolder, testMethod.methodName)
    if (!dir.mkdirs()) {
      throw IOException("project dir '${dir.absolutePath}' creation failed")
    }
    dir.canonicalPath
  }

  fun robot() = guiTestRule.robot()

  //********************KOTLIN DSL FOR GUI TESTING*************************
  //*********CONTEXT LAMBDA FUNCTIONS WITH RECEIVERS***********************
  /**
   * Context function: finds welcome frame and creates WelcomeFrameFixture instance as receiver object. Code block after it call methods on
   * the receiver object (WelcomeFrameFixture instance).
   */
  open fun welcomeFrame(timeout: Timeout = Timeouts.minutes05, body: WelcomeFrameFixture.() -> Unit) {
    step("at Welcome frame") {
      body(guiTestRule.findWelcomeFrame(timeout))
    }
  }

  /**
   * Context function: finds IDE frame and creates IdeFrameFixture instance as receiver object. Code block after it call methods on the
   * receiver object (IdeFrameFixture instance).
   */
  fun ideFrame(timeout: Timeout = Timeouts.defaultTimeout, func: IdeFrameFixture.() -> Unit) {
    step("at IDE frame") { func(guiTestRule.findIdeFrame(timeout)) }
  }

  /**
   * Context function: finds dialog with specified title and creates JDialogFixture instance as receiver object. Code block after it call
   * methods on the receiver object (JDialogFixture instance).
   *
   * @title title of searching dialog window. If dialog should be only one title could be omitted or set to null.
   * @needToKeepDialog is true if no need to wait when dialog is closed
   * @timeout time in seconds to find dialog in GUI hierarchy.
   */
  fun dialog(title: String? = null,
             ignoreCaseTitle: Boolean = false,
             predicate: FinderPredicate = Predicate.equality,
             timeout: Timeout = Timeouts.defaultTimeout,
             needToKeepDialog: Boolean = false,
             func: JDialogFixture.() -> Unit) {
    step("at '$title' dialog") {
      val dialog = dialog(
        title = title,
        ignoreCaseTitle = ignoreCaseTitle,
        predicate = predicate,
        timeout = timeout
      )
      func(dialog)
      if (!needToKeepDialog) dialog.waitTillGone()
    }
  }

  fun dialogWithTextComponent(timeout: Timeout, predicate: (JTextComponent) -> Boolean, func: JDialogFixture.() -> Unit) {
    step("at dialog with text component") {
      val dialog: JDialog = waitUntilFound(null, JDialog::class.java, timeout) {
        JDialogFixture(robot(), it).containsChildComponent(predicate)
      }
      val dialogFixture = JDialogFixture(robot(), dialog)
      func(dialogFixture)
      dialogFixture.waitTillGone()
    }
  }

  fun settingsDialog(timeout: Timeout = Timeouts.defaultTimeout,
                     needToKeepDialog: Boolean = false,
                     func: JDialogFixture.() -> Unit) {
    if (isMac()) dialog(title = "Preferences", func = func)
    else dialog(title = "Settings", func = func)
  }

  fun pluginDialog(timeout: Timeout = Timeouts.defaultTimeout, needToKeepDialog: Boolean = false, func: PluginDialogFixture.() -> Unit) {
    step("at 'Plugins' dialog") {
      val pluginDialog = PluginDialogFixture(robot(), findDialog("Plugins", false, timeout))
      func(pluginDialog)
      if (!needToKeepDialog) pluginDialog.waitTillGone()
    }
  }

  fun pluginDialog(timeout: Timeout = Timeouts.defaultTimeout): PluginDialogFixture {
    return PluginDialogFixture(robot(), findDialog("Plugins", false, timeout))
  }

  /**
   * Waits for a native file chooser, types the path in a textfield and closes it by clicking OK button. Or runs AppleScript if the file chooser
   * is a Mac native.
   */
  fun chooseFileInFileChooser(path: String, timeout: Timeout = Timeouts.defaultTimeout, needToRefresh: Boolean = false) {
    step("choose '$path' file in File Chooser") {
      val macNativeFileChooser = SystemInfo.isMac() && (System.getProperty("ide.mac.file.chooser.native", "true").toLowerCase() == "false")
      if (macNativeFileChooser) {
        MacFileChooserDialogFixture(robot()).selectByPath(path)
      }
      else {
        val fileChooserDialog: JDialog
        try {
          fileChooserDialog = GuiTestUtilKt.withPauseWhenNull(timeout = timeout) {
            robot().finder()
              .findAll(GuiTestUtilKt.typeMatcher(JDialog::class.java) { true })
              .firstOrNull {
                GuiTestUtilKt.findAllWithBFS(it, JPanel::class.java).any { panel ->
                  panel.javaClass.name.contains(FileChooserDialogImpl::class.java.simpleName)
                }
              }
          }
        }
        catch (timeoutError: WaitTimedOutError) {
          throw ComponentLookupException("Unable to find file chooser dialog in ${timeout.toPrintable()}")
        }
        val dialogFixture = JDialogFixture(robot(), fileChooserDialog)
        with(dialogFixture) {
          asyncProcessIcon().waitUntilStop(Timeouts.seconds30)
          textfield("")
          invokeAction("\$SelectAll")
          typeText(path)
          if (needToRefresh) {
            tryWithPause(ComponentLookupException::class.java, "Path is located in the tree", Timeouts.seconds10) {
              actionButton("Refresh").click()
              textfield("").deleteText()
              typeText(path)
            }
          }
          button("OK").clickWhenEnabled()
          waitTillGone()
        }
      }
    }
  }

  /**
   * Context function: imports a simple project to skip steps of creation, creates IdeFrameFixture instance as receiver object when project
   * is loaded. Code block after it call methods on the receiver object (IdeFrameFixture instance).
   */
  fun simpleProject(func: IdeFrameFixture.() -> Unit) {
    step("at simple project") {
      func(guiTestRule.importSimpleProject())
    }
  }

  /**
   * Context function: finds dialog "New Project Wizard" and creates NewProjectWizardFixture instance as receiver object. Code block after
   * it call methods on the receiver object (NewProjectWizardFixture instance).
   */
  fun projectWizard(func: NewProjectWizardFixture.() -> Unit) {
    step("at New Project wizard") {
      func(guiTestRule.findNewProjectWizard())
    }
  }

  /**
   * Context function for IdeFrame: activates project view in IDE and creates ProjectViewFixture instance as receiver object. Code block after
   * it call methods on the receiver object (ProjectViewFixture instance).
   */
  fun IdeFrameFixture.projectView(func: ProjectViewFixture.() -> Unit) {
    step("at Project view") {
      func(this.projectView)
    }
  }

  /**
   * Context function for IdeFrame: activates toolwindow view in IDE and creates CustomToolWindowFixture instance as receiver object. Code
   * block after it call methods on the receiver object (CustomToolWindowFixture instance).
   *
   * @id - a toolwindow id.
   */
  fun IdeFrameFixture.toolwindow(id: String, func: CustomToolWindowFixture.() -> Unit) {
    step("at '$id' tool window") {
      func(CustomToolWindowFixture(id, this))
    }
  }

  //*********FIXTURES METHODS FOR IDEFRAME WITHOUT ROBOT and TARGET

  /**
   * Context function for IdeFrame: get current editor and create EditorFixture instance as a receiver object. Code block after
   * it call methods on the receiver object (EditorFixture instance).
   */
  fun IdeFrameFixture.editor(func: FileEditorFixture.() -> Unit) {
    step("at editor") {
      func(this.editor)
    }
  }

  /**
   * Context function for IdeFrame: get the tab with specific opened file and create EditorFixture instance as a receiver object. Code block after
   * it call methods on the receiver object (EditorFixture instance).
   */
  fun IdeFrameFixture.editor(tabName: String, func: FileEditorFixture.() -> Unit) {
    step("at '$tabName' tab of editor") {
      val editorFixture = this.editor.selectTab(tabName)
      func(editorFixture)
    }
  }

  /**
   * Context function for IdeFrame: creates a MainToolbarFixture instance as receiver object. Code block after
   * it call methods on the receiver object (MainToolbarFixture instance).
   */
  fun IdeFrameFixture.toolbar(func: MainToolbarFixture.() -> Unit) {
    step("at toolbar") {
      func(this.toolbar)
    }
  }

  /**
   * Context function for IdeFrame: creates a NavigationBarFixture instance as receiver object. Code block after
   * it call methods on the receiver object (NavigationBarFixture instance).
   */
  fun IdeFrameFixture.navigationBar(func: NavigationBarFixture.() -> Unit) {
    step("at navigation bar") {
      func(this.navigationBar)
    }
  }

  fun IdeFrameFixture.configurationList(func: RunConfigurationListFixture.() -> Unit) {
    step("at configuration list") {
      func(this.runConfigurationList)
    }
  }

  fun IdeFrameFixture.gutter(func: GutterFixture.() -> Unit) {
    step("at gutter panel") {
      func(this.gutter)
    }
  }

  /**
   * Extension function for IDE to iterate through the menu.
   *
   * @path items like: popup("New", "File")
   */
  @Deprecated(message = "Should be replaced with menu(*path).click()", replaceWith = ReplaceWith("menu(*path).click()"))
  fun IdeFrameFixture.popup(vararg path: String) = this.invokeMenuPath(*path)

  fun IdeFrameFixture.menu(vararg path: String): MenuFixture.MenuItemFixture = this.getMenuPath(*path)

  fun CustomToolWindowFixture.ContentFixture.editor(func: EditorFixture.() -> Unit) {
    func(this.editor())
  }

  fun CustomToolWindowFixture.ContentFixture.editorContainingText(text: String, func: EditorFixture.() -> Unit) {
    func(findEditorContainingText(text))
  }

  //*********COMMON FUNCTIONS WITHOUT CONTEXT
  /**
   * Type text by symbol with a constant delay. Generate system key events, so entered text will aply to a focused component.
   */
  fun typeText(text: String) = GuiTestUtil.typeText(text, robot(), 10)

  /**
   * @param keyStroke should follow {@link KeyStrokeAdapter#getKeyStroke(String)} instructions and be generated by {@link KeyStrokeAdapter#toString(KeyStroke)} preferably
   *
   * examples: shortcut("meta comma"), shortcut("ctrl alt s"), shortcut("alt f11")
   * modifiers order: shift | ctrl | control | meta | alt | altGr | altGraph
   */
  fun shortcut(keyStroke: String) = GuiTestUtil.invokeActionViaShortcut(keyStroke)

  fun shortcut(shortcut: Shortcut) = shortcut(shortcut.getKeystroke())

  fun shortcut(winShortcut: Shortcut, macShortcut: Shortcut) {
    if (SystemInfo.isMac()) shortcut(macShortcut)
    else shortcut(winShortcut)
  }

  fun shortcut(key: Key) = shortcut(key.name)

  /**
   * copies a given string to a system clipboard
   */
  fun copyToClipboard(string: String) = Clipboard.copyToClipboard(string)

  /**
   * Invoke action by actionId through its keystroke
   */
  fun invokeAction(actionId: String) = GuiTestUtil.invokeAction(actionId)

  /**
   * Take a screenshot for a specific component. Screenshot remain scaling and represent it in name of file.
   */
  fun screenshot(component: Component, screenshotName: String) {

    val extension = "${getScaleSuffix()}.jpg"
    val pathWithTestFolder = testScreenshotDirPath.path + slash + this.guiTestRule.getTestName()
    val fileWithTestFolder = File(pathWithTestFolder)
    FileUtil.ensureExists(fileWithTestFolder)
    var screenshotFilePath = File(fileWithTestFolder, screenshotName + extension)
    if (screenshotFilePath.isFile) {
      val format = SimpleDateFormat("MM-dd-yyyy.HH.mm.ss")
      val now = format.format(GregorianCalendar().time)
      screenshotFilePath = File(fileWithTestFolder, "$screenshotName.$now$extension")
    }
    screenshotTaker.safeTakeScreenshotAndSave(screenshotFilePath, component)
    println(message = "Screenshot for a component \"$component\" taken and stored at ${screenshotFilePath.path}")

  }


  /**
   * Finds JDialog with a specific title (if title is null showing dialog should be only one) and returns created JDialogFixture
   */
  fun dialog(title: String? = null,
             ignoreCaseTitle: Boolean,
             predicate: FinderPredicate = Predicate.equality,
             timeout: Timeout = Timeouts.defaultTimeout): JDialogFixture {
    if (title == null) {
      val jDialog = waitUntilFound(null, JDialog::class.java, timeout) { true }
      return JDialogFixture(robot(), jDialog)
    }
    else {
      try {
        val dialog = GuiTestUtilKt.withPauseWhenNull(timeout = timeout) {
          val allMatchedDialogs = robot().finder().findAll(typeMatcher(JDialog::class.java) {
            if (ignoreCaseTitle) predicate(it.title.toLowerCase(), title.toLowerCase()) else predicate(it.title, title)
          }).filter { it.isShowing && it.isEnabled && it.isVisible }
          if (allMatchedDialogs.size > 1) throw Exception(
            "Found more than one (${allMatchedDialogs.size}) dialogs matched title \"$title\"")
          allMatchedDialogs.firstOrNull()
        }
        return JDialogFixture(robot(), dialog)
      }
      catch (timeoutError: WaitTimedOutError) {
        throw ComponentLookupException("Timeout error for finding JDialog by title \"$title\" for ${timeout.toPrintable()}")
      }
    }
  }

  /**
   * Finds JDialog with a specific title (if title is null showing dialog should be only one)
   */
  private fun findDialog(title: String?, ignoreCaseTitle: Boolean, timeout: Timeout): JDialog =
    waitUntilFound(null, JDialog::class.java, timeout) {
      title?.equals(it.title, ignoreCaseTitle)?.and(it.isShowing && it.isEnabled && it.isVisible) ?: true
    }

  fun exists(fixture: () -> AbstractComponentFixture<*, *, *>): Boolean {
    try {
      fixture.invoke()
    }
    catch (ex: Exception) {
      when (ex) {
        is ComponentLookupException,
        is WaitTimedOutError -> {
          return false
        }
        else -> throw ex
      }
    }
    return true
  }

  //*********SOME EXTENSION FUNCTIONS FOR FIXTURES

  fun JListFixture.doubleClickItem(itemName: String) {
    this.item(itemName).doubleClick()
  }

  //necessary only for Windows
  private fun getScaleSuffix(): String? {
    val scaleEnabled: Boolean = (GuiTestUtil.getSystemPropertyOrEnvironmentVariable("sun.java2d.uiScale.enabled")?.toLowerCase().equals(
      "true"))
    if (!scaleEnabled) return ""
    val uiScaleVal = GuiTestUtil.getSystemPropertyOrEnvironmentVariable("sun.java2d.uiScale") ?: return ""
    return "@${uiScaleVal}x"
  }

  fun tableRowValues(table: JTableFixture, rowIndex: Int): List<String> {
    val fixture = ExtendedTableFixture(robot(), table.target())
    return RowFixture(robot(), rowIndex, fixture).values()
  }

  fun tableRowCount(table: JTableFixture): Int {
    val fixture = ExtendedTableFixture(robot(), table.target())
    return fixture.rowCount()
  }

  /**
   * Wait for panel with specified title disappearing
   * @param panelTitle title of investigated panel
   * @param timeoutToAppear timeout to wait when the panel appears
   * @param timeoutToDisappear timeout to wait when the panel disappears (after it has appeared)
   * @throws ComponentLookupException if the panel hasn't appeared after [timeoutToAppear] finishing
   * @throws WaitTimedOutError if the panel appears, but cannot disappear after [timeoutToDisappear] finishing
   * */
  fun waitForPanelToDisappear(
    panelTitle: String,
    timeoutToAppear: Timeout = Timeouts.minutes05,
    timeoutToDisappear: Timeout = Timeouts.minutes05) {
    try {
      Pause.pause(object : Condition("Wait for $panelTitle panel appears") {
        override fun test(): Boolean {
          try {
            robot().finder().find(guiTestRule.findIdeFrame().target()) {
              it is JLabel && it.text == panelTitle
            }
          }
          catch (cle: ComponentLookupException) {
            return false
          }
          return true
        }
      }, timeoutToAppear)
    }
    catch (e: WaitTimedOutError) {
      throw ComponentLookupException("Panel `$panelTitle` hasn't appear")
    }

    Pause.pause(object : Condition("Wait for $panelTitle panel disappears") {
      override fun test(): Boolean {
        try {
          robot().finder().find(guiTestRule.findIdeFrame().target()) {
            it is JLabel && it.text == panelTitle
          }
        }
        catch (cle: ComponentLookupException) {
          return true
        }
        return false
      }
    }, timeoutToDisappear)

  }

  @Before
  open fun setUp() {
    logStartTest(testMethod.methodName)
  }

  @After
  open fun tearDown() {
    logEndTest(testMethod.methodName)
  }

  open fun isIdeFrameRun(): Boolean = true

}
