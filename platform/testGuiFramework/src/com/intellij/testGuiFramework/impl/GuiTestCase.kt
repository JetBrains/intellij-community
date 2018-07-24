/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.impl

import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testGuiFramework.fixtures.*
import com.intellij.testGuiFramework.fixtures.extended.ExtendedTableFixture
import com.intellij.testGuiFramework.fixtures.extended.RowFixture
import com.intellij.testGuiFramework.fixtures.newProjectWizard.NewProjectWizardFixture
import com.intellij.testGuiFramework.framework.GuiTestLocalRunner
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.GuiTestUtil.defaultTimeout
import com.intellij.testGuiFramework.framework.IdeTestApplication.getTestScreenshotDirPath
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.typeMatcher
import com.intellij.testGuiFramework.launcher.system.SystemInfo
import com.intellij.testGuiFramework.launcher.system.SystemInfo.isMac
import com.intellij.testGuiFramework.util.Clipboard
import com.intellij.testGuiFramework.util.Key
import com.intellij.testGuiFramework.util.Shortcut
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.AbstractComponentFixture
import org.fest.swing.fixture.JListFixture
import org.fest.swing.fixture.JTableFixture
import org.fest.swing.image.ScreenshotTaker
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import org.junit.Rule
import org.junit.runner.RunWith
import java.awt.Component
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel

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
  val guiTestRule = GuiTestRule()

  val settingsTitle: String = if (isMac()) "Preferences" else "Settings"
  //  val defaultSettingsTitle: String = if (isMac()) "Default Preferences" else "Default Settings"
  val defaultSettingsTitle: String = if (isMac()) "Preferences for New Projects" else "Settings for New Projects"
  val slash: String = File.separator

  fun robot() = guiTestRule.robot()

  //********************KOTLIN DSL FOR GUI TESTING*************************
  //*********CONTEXT LAMBDA FUNCTIONS WITH RECEIVERS***********************
  /**
   * Context function: finds welcome frame and creates WelcomeFrameFixture instance as receiver object. Code block after it call methods on
   * the receiver object (WelcomeFrameFixture instance).
   */
  open fun welcomeFrame(func: WelcomeFrameFixture.() -> Unit) {
    func(guiTestRule.findWelcomeFrame())
  }

  /**
   * Context function: finds IDE frame and creates IdeFrameFixture instance as receiver object. Code block after it call methods on the
   * receiver object (IdeFrameFixture instance).
   */
  fun ideFrame(func: IdeFrameFixture.() -> Unit) {
    func(guiTestRule.findIdeFrame())
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
             timeout: Long = defaultTimeout,
             needToKeepDialog: Boolean = false,
             func: JDialogFixture.() -> Unit) {
    val dialog = dialog(title, ignoreCaseTitle, timeout)
    func(dialog)
    if (!needToKeepDialog) dialog.waitTillGone()
  }


  /**
   * Waits for a native file chooser, types the path in a textfield and closes it by clicking OK button. Or runs AppleScript if the file chooser
   * is a Mac native.
   */
  fun chooseFileInFileChooser(path: String, timeout: Long = defaultTimeout) {
    val macNativeFileChooser = SystemInfo.isMac() && (System.getProperty("ide.mac.file.chooser.native", "true").toLowerCase() == "false")
    if (macNativeFileChooser) {
      MacFileChooserDialogFixture(robot()).selectByPath(path)
    }
    else {
      val fileChooserDialog: JDialog
      try {
        fileChooserDialog = GuiTestUtilKt.withPauseWhenNull(timeout.toInt()) {
          robot().finder()
            .findAll(GuiTestUtilKt.typeMatcher(JDialog::class.java) { true })
            .firstOrNull {
              GuiTestUtilKt.findAllWithBFS(it, JPanel::class.java).any {
                it.javaClass.name.contains(FileChooserDialogImpl::class.java.simpleName)
              }
            }
        }
      }
      catch (timeoutError: WaitTimedOutError) {
        throw ComponentLookupException("Unable to find file chooser dialog in ${timeout.toInt()} seconds")
      }
      val dialogFixture = JDialogFixture(robot(), fileChooserDialog)
      with(dialogFixture) {
        asyncProcessIcon().waitUntilStop(20)
        textfield("")
        invokeAction("\$SelectAll")
        typeText(path)
        button("OK").clickWhenEnabled()
        waitTillGone()
      }
    }
  }

  /**
   * Context function: imports a simple project to skip steps of creation, creates IdeFrameFixture instance as receiver object when project
   * is loaded. Code block after it call methods on the receiver object (IdeFrameFixture instance).
   */
  fun simpleProject(func: IdeFrameFixture.() -> Unit) {
    func(guiTestRule.importSimpleProject())
  }

  /**
   * Context function: finds dialog "New Project Wizard" and creates NewProjectWizardFixture instance as receiver object. Code block after
   * it call methods on the receiver object (NewProjectWizardFixture instance).
   */
  fun projectWizard(func: NewProjectWizardFixture.() -> Unit) {
    func(guiTestRule.findNewProjectWizard())
  }

  /**
   * Context function for IdeFrame: activates project view in IDE and creates ProjectViewFixture instance as receiver object. Code block after
   * it call methods on the receiver object (ProjectViewFixture instance).
   */
  fun IdeFrameFixture.projectView(func: ProjectViewFixture.() -> Unit) {
    func(this.projectView)
  }

  /**
   * Context function for IdeFrame: activates toolwindow view in IDE and creates CustomToolWindowFixture instance as receiver object. Code
   * block after it call methods on the receiver object (CustomToolWindowFixture instance).
   *
   * @id - a toolwindow id.
   */
  fun IdeFrameFixture.toolwindow(id: String, func: CustomToolWindowFixture.() -> Unit) {
    func(CustomToolWindowFixture(id, this))
  }

  //*********FIXTURES METHODS FOR IDEFRAME WITHOUT ROBOT and TARGET

  /**
   * Context function for IdeFrame: get current editor and create EditorFixture instance as a receiver object. Code block after
   * it call methods on the receiver object (EditorFixture instance).
   */
  fun IdeFrameFixture.editor(func: FileEditorFixture.() -> Unit) {
    func(this.editor)
  }

  /**
   * Context function for IdeFrame: get the tab with specific opened file and create EditorFixture instance as a receiver object. Code block after
   * it call methods on the receiver object (EditorFixture instance).
   */
  fun IdeFrameFixture.editor(tabName: String, func: FileEditorFixture.() -> Unit) {
    val editorFixture = this.editor.selectTab(tabName)
    func(editorFixture)
  }

  /**
   * Context function for IdeFrame: creates a MainToolbarFixture instance as receiver object. Code block after
   * it call methods on the receiver object (MainToolbarFixture instance).
   */
  fun IdeFrameFixture.toolbar(func: MainToolbarFixture.() -> Unit) {
    func(this.toolbar)
  }

  /**
   * Context function for IdeFrame: creates a NavigationBarFixture instance as receiver object. Code block after
   * it call methods on the receiver object (NavigationBarFixture instance).
   */
  fun IdeFrameFixture.navigationBar(func: NavigationBarFixture.() -> Unit) {
    func(this.navigationBar)
  }

  fun IdeFrameFixture.configurationList(func: RunConfigurationListFixture.() -> Unit) {
    func(this.runConfigurationList)
  }

  fun IdeFrameFixture.gutter(func: GutterFixture.() -> Unit) {
    func(this.gutter)
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

    val extension = "${getScaleSuffix()}.png"
    val pathWithTestFolder = getTestScreenshotDirPath().path + slash + this.guiTestRule.getTestName()
    val fileWithTestFolder = File(pathWithTestFolder)
    FileUtil.ensureExists(fileWithTestFolder)
    var screenshotFilePath = File(fileWithTestFolder, screenshotName + extension)
    if (screenshotFilePath.isFile) {
      val format = SimpleDateFormat("MM-dd-yyyy.HH.mm.ss")
      val now = format.format(GregorianCalendar().time)
      screenshotFilePath = File(fileWithTestFolder, "$screenshotName.$now$extension")
    }
    ScreenshotTaker().saveComponentAsPng(component, screenshotFilePath.path)
    println(message = "Screenshot for a component \"$component\" taken and stored at ${screenshotFilePath.path}")

  }


  /**
   * Finds JDialog with a specific title (if title is null showing dialog should be only one) and returns created JDialogFixture
   */
  fun dialog(title: String? = null, ignoreCaseTitle: Boolean, timeoutInSeconds: Long): JDialogFixture {
    if (title == null) {
      val jDialog = waitUntilFound(null, JDialog::class.java, timeoutInSeconds) { true }
      return JDialogFixture(robot(), jDialog)
    }
    else {
      try {
        val dialog = GuiTestUtilKt.withPauseWhenNull(timeoutInSeconds.toInt()) {
          val allMatchedDialogs = robot().finder().findAll(typeMatcher(JDialog::class.java) {
            if (ignoreCaseTitle) it.title.toLowerCase() == title.toLowerCase() else it.title == title
          }).filter { it.isShowing && it.isEnabled && it.isVisible }
          if (allMatchedDialogs.size > 1) throw Exception(
            "Found more than one (${allMatchedDialogs.size}) dialogs matched title \"$title\"")
          allMatchedDialogs.firstOrNull()
        }
        return JDialogFixture(robot(), dialog)
      }
      catch (timeoutError: WaitTimedOutError) {
        throw ComponentLookupException("Timeout error for finding JDialog by title \"$title\" for $timeoutInSeconds seconds")
      }
    }
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


  fun pause(condition: String = "Unspecified condition", timeoutSeconds: Long = 120, testFunction: () -> Boolean) {
    Pause.pause(object : Condition(condition) {
      override fun test() = testFunction()
    }, Timeout.timeout(timeoutSeconds, TimeUnit.SECONDS))
  }

  fun tableRowValues(table: JTableFixture, rowIndex: Int): List<String> {
    val fixture = ExtendedTableFixture(robot(), table.target())
    return RowFixture(robot(), rowIndex, fixture).values()
  }

  fun tableRowCount(table: JTableFixture): Int {
    val fixture = ExtendedTableFixture(robot(), table.target())
    return fixture.rowCount()
  }

  fun waitForPanelToDisappear(panelTitle: String, timeout: Long = 300000) {
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
    }, timeout)

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
    })

  }

}
