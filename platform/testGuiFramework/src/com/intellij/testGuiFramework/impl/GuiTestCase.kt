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

import com.intellij.ide.GeneralSettings
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testGuiFramework.cellReader.ExtendedJComboboxCellReader
import com.intellij.testGuiFramework.cellReader.ExtendedJListCellReader
import com.intellij.testGuiFramework.cellReader.ExtendedJTableCellReader
import com.intellij.testGuiFramework.fixtures.*
import com.intellij.testGuiFramework.fixtures.extended.ExtendedButtonFixture
import com.intellij.testGuiFramework.fixtures.extended.ExtendedTreeFixture
import com.intellij.testGuiFramework.fixtures.newProjectWizard.NewProjectWizardFixture
import com.intellij.testGuiFramework.framework.GuiTestBase
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.GuiTestUtil.waitUntilFound
import com.intellij.testGuiFramework.framework.IdeTestApplication.getTestScreenshotDirPath
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.findBoundedComponentByText
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.getComponentText
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.isTextComponent
import com.intellij.testGuiFramework.launcher.system.SystemInfo.isMac
import com.intellij.ui.CheckboxTree
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.net.HttpConfigurable
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.SmartWaitRobot
import org.fest.swing.exception.ActionFailedException
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.AbstractComponentFixture
import org.fest.swing.fixture.JListFixture
import org.fest.swing.fixture.JTableFixture
import org.fest.swing.fixture.JTextComponentFixture
import org.fest.swing.image.ScreenshotTaker
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import org.fest.swing.timing.Timeout.timeout
import java.awt.Component
import java.awt.Container
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.*
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
open class GuiTestCase : GuiTestBase() {

  class GuiSettings internal constructor() {

    init {
      GeneralSettings.getInstance().isShowTipsOnStartup = false
      GuiTestUtil.setUpDefaultProjectCreationLocationPath()
      val ideSettings = HttpConfigurable.getInstance()
      ideSettings.USE_HTTP_PROXY = false
      ideSettings.PROXY_HOST = ""
      ideSettings.PROXY_PORT = 80
    }

    companion object {

      private val lock = Any()

      private var SETTINGS: GuiSettings? = null

      fun setUp(): GuiSettings {
        synchronized(lock) {
          if (SETTINGS == null) SETTINGS = GuiSettings()
          return SETTINGS as GuiSettings
        }
      }
    }

  }

  /**
   * default timeout to find target component for fixture. Using seconds as time unit.
   */
  var defaultTimeout = 120L

  val screenshotTaker = ScreenshotTaker()
  var pathToSaveScreenshots = getTestScreenshotDirPath()


  val settingsTitle: String = if (isMac()) "Preferences" else "Settings"
  val defaultSettingsTitle: String = if (isMac()) "Default Preferences" else "Default Settings"
  val slash: String = File.separator


  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myRobot = SmartWaitRobot()
    GuiSettings.setUp()
  }

  @Throws(InvocationTargetException::class, InterruptedException::class)
  override fun tearDown() {
    super.tearDown()
  }

  companion object {
    val IS_UNDER_TEAMCITY = System.getenv("TEAMCITY_VERSION") != null
  }


  //********************KOTLIN DSL FOR GUI TESTING*************************
  //*********CONTEXT LAMBDA FUNCTIONS WITH RECEIVERS***********************
  /**
   * Context function: finds welcome frame and creates WelcomeFrameFixture instance as receiver object. Code block after it call methods on
   * the receiver object (WelcomeFrameFixture instance).
   */
  open fun welcomeFrame(func: WelcomeFrameFixture.() -> Unit) {
    func(findWelcomeFrame())
  }

  /**
   * Context function: finds IDE frame and creates IdeFrameFixture instance as receiver object. Code block after it call methods on the
   * receiver object (IdeFrameFixture instance).
   */
  fun ideFrame(func: IdeFrameFixture.() -> Unit) {
    func(findIdeFrame())
  }

  /**
   * Context function: finds dialog with specified title and creates JDialogFixture instance as receiver object. Code block after it call
   * methods on the receiver object (JDialogFixture instance).
   *
   * @title title of searching dialog window. If dialog should be only one title could be omitted or set to null.
   * @timeout time in seconds to find dialog in GUI hierarchy.
   */
  fun dialog(title: String? = null, timeout: Long = defaultTimeout, func: JDialogFixture.() -> Unit) {
    val dialog = dialog(title, timeout)
    func(dialog)
    dialog.waitTillGone()
  }

  /**
   * Context function: imports a simple project to skip steps of creation, creates IdeFrameFixture instance as receiver object when project
   * is loaded. Code block after it call methods on the receiver object (IdeFrameFixture instance).
   */
  fun simpleProject(func: IdeFrameFixture.() -> Unit) {
    func(importSimpleProject())
  }

  /**
   * Context function: finds dialog "New Project Wizard" and creates NewProjectWizardFixture instance as receiver object. Code block after
   * it call methods on the receiver object (NewProjectWizardFixture instance).
   */
  fun projectWizard(func: NewProjectWizardFixture.() -> Unit) {
    func(findNewProjectWizard())
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

  //*********FIXTURES METHODS WITHOUT ROBOT and TARGET; KOTLIN ONLY
  /**
   * Finds a JList component in hierarchy of context component with a containingItem and returns JListFixture.
   *
   * @timeout in seconds to find JList component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.jList(containingItem: String? = null,
                                                      timeout: Long = defaultTimeout): JListFixture = if (target() is Container) jList(
    target() as Container, containingItem, timeout)
  else throw UnsupportedOperationException("Sorry, unable to find JList component with ${target().toString()} as a Container")

  /**
   * Finds a JButton component in hierarchy of context component with a name and returns ExtendedButtonFixture.
   *
   * @timeout in seconds to find JButton component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.button(name: String,
                                                       timeout: Long = defaultTimeout): ExtendedButtonFixture = if (target() is Container) button(
    target() as Container, name, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find JButton component named by \"${name}\" with ${target().toString()} as a Container")

  /**
   * Finds a ComponentWithBrowseButton component in hierarchy of context component with a name and returns ComponentWithBrowseButtonFixture.
   *
   * @timeout in seconds to find ComponentWithBrowseButton component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.componentWithBrowseButton(comboboxClass: Class<out ComponentWithBrowseButton<out JComponent>>,
                                                                          timeout: Long = defaultTimeout): ComponentWithBrowseButtonFixture
    = if (target() is Container) componentWithBrowseButton(target() as Container, comboboxClass, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find ComponentWithBrowseButton component with ${target().toString()} as a Container")


  /**
   * Finds a JComboBox component in hierarchy of context component by text of label and returns ComboBoxFixture.
   *
   * @timeout in seconds to find JComboBox component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.combobox(labelText: String,
                                                         timeout: Long = defaultTimeout): ComboBoxFixture = if (target() is Container) combobox(
    target() as Container, labelText, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find JComboBox component near label by \"${labelText}\" with ${target().toString()} as a Container")


  /**
   * Finds a JCheckBox component in hierarchy of context component by text of label and returns CheckBoxFixture.
   *
   * @timeout in seconds to find JCheckBox component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.checkbox(labelText: String,
                                                         timeout: Long = defaultTimeout): CheckBoxFixture = if (target() is Container) checkbox(
    target() as Container, labelText, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find JCheckBox component near label by \"${labelText}\" with ${target().toString()} as a Container")

  /**
   * Finds a ActionLink component in hierarchy of context component by name and returns ActionLinkFixture.
   *
   * @timeout in seconds to find ActionLink component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.actionLink(name: String,
                                                           timeout: Long = defaultTimeout): ActionLinkFixture = if (target() is Container) actionLink(
    target() as Container, name, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find ActionLink component by name \"${name}\" with ${target().toString()} as a Container")

  /**
   * Finds a ActionButton component in hierarchy of context component by action name and returns ActionButtonFixture.
   *
   * @actionName text or action id of an action button (@see com.intellij.openapi.actionSystem.ActionManager#getId())
   * @timeout in seconds to find ActionButton component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.actionButton(actionName: String,
                                                             timeout: Long = defaultTimeout): ActionButtonFixture = if (target() is Container) actionButton(
    target() as Container, actionName, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find ActionButton component by action name \"${actionName}\" with ${target().toString()} as a Container")

  /**
   * Finds a ActionButton component in hierarchy of context component by action class name and returns ActionButtonFixture.
   *
   * @actionClassName qualified name of class for action
   * @timeout in seconds to find ActionButton component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.actionButtonByClass(actionClassName: String,
                                                                    timeout: Long = defaultTimeout): ActionButtonFixture = if (target() is Container) actionButtonByClass(
    target() as Container, actionClassName, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find ActionButton component by action class name \"${actionClassName}\" with ${target().toString()} as a Container")

  /**
   * Finds a JRadioButton component in hierarchy of context component by label text and returns JRadioButtonFixture.
   *
   * @timeout in seconds to find JRadioButton component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.radioButton(textLabel: String,
                                                            timeout: Long = defaultTimeout): RadioButtonFixture = if (target() is Container) radioButton(
    target() as Container, textLabel, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find RadioButton component by label \"${textLabel}\" with ${target().toString()} as a Container")

  /**
   * Finds a JTextComponent component (JTextField) in hierarchy of context component by text of label and returns JTextComponentFixture.
   *
   * @textLabel could be a null if label is absent
   * @timeout in seconds to find JTextComponent component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.textfield(textLabel: String?,
                                                          timeout: Long = defaultTimeout): JTextComponentFixture = if (target() is Container) textfield(
    target() as Container, textLabel, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find JTextComponent (JTextField) component by label \"${textLabel}\" with ${target().toString()} as a Container")

  /**
   * Finds a JTree component in hierarchy of context component by a path and returns ExtendedTreeFixture.
   *
   * @pathStrings comma separated array of Strings, representing path items: jTree("myProject", "src", "Main.java")
   * @timeout in seconds to find JTree component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.jTree(vararg pathStrings: String,
                                                      timeout: Long = defaultTimeout): ExtendedTreeFixture = if (target() is Container) jTreePath(
    target() as Container, timeout, *pathStrings)
  else throw UnsupportedOperationException(
    "Sorry, unable to find JTree component \"${if (pathStrings.isNotEmpty()) "by path ${pathStrings}" else ""}\" with ${target().toString()} as a Container")

  /**
   * Finds a CheckboxTree component in hierarchy of context component by a path and returns CheckboxTreeFixture.
   *
   * @pathStrings comma separated array of Strings, representing path items: checkboxTree("JBoss", "JBoss Drools")
   * @timeout in seconds to find JTree component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.checkboxTree(vararg pathStrings: String,
                                                             timeout: Long = defaultTimeout): CheckboxTreeFixture = if (target() is Container) checkboxTree(
    target() as Container, timeout, *pathStrings)
  else throw UnsupportedOperationException(
    "Sorry, unable to find CheckboxTree component \"${if (pathStrings.isNotEmpty()) "by path ${pathStrings}" else ""}\" with ${target().toString()} as a Container")

  /**
   * Finds a JTable component in hierarchy of context component by a cellText and returns JTableFixture.
   *
   * @timeout in seconds to find JTable component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.table(cellText: String,
                                                      timeout: Long = defaultTimeout): JTableFixture = if (target() is Container) table(
    target() as Container, cellText, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find JTable component with cell text \"$cellText\" with ${target().toString()} as a Container")

  /**
   * Finds popup on screen with item (itemName) and clicks on it item
   *
   * @timeout timeout in seconds to find JTextComponent component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.popupClick(itemName: String,
                                                           timeout: Long = defaultTimeout) = if (target() is Container) popupClick(
    target() as Container, itemName, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find Popup component with ${target().toString()} as a Container")

  /**
   * Finds a LinkLabel component in hierarchy of context component by a link name and returns fixture for it.
   *
   * @timeout in seconds to find LinkLabel component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.linkLabel(linkName: String, /*timeout in seconds*/
                                                          timeout: Long = defaultTimeout) = if (target() is Container) linkLabel(
    target() as Container, linkName, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find LinkLabel component with ${target().toString()} as a Container")


  fun <S, C : Component> ComponentFixture<S, C>.hyperlinkLabel(labelText: String, /*timeout in seconds*/
                                                               timeout: Long = defaultTimeout): HyperlinkLabelFixture
    = if (target() is Container) hyperlinkLabel(target() as Container, labelText, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find HyperlinkLabel component by label text: \"$labelText\" with ${target().toString()} as a Container")

  /**
   * Finds a table of plugins component in hierarchy of context component by a link name and returns fixture for it.
   *
   * @timeout in seconds to find table of plugins component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.pluginTable(timeout: Long = defaultTimeout)
    = if (target() is Container) pluginTable(target() as Container, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find PluginTable component with ${target().toString()} as a Container")

  /**
   * Finds a Message component in hierarchy of context component by a title MessageFixture.
   *
   * @timeout in seconds to find component for Message
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.message(title: String, timeout: Long = defaultTimeout)
    = if (target() is Container) message(target() as Container, title, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find PluginTable component with ${target().toString()} as a Container")


  /**
   * Finds a Message component in hierarchy of context component by a title MessageFixture.
   *
   * @timeout in seconds to find component for Message
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.message(title: String, timeout: Long = defaultTimeout, func: MessagesFixture.() -> Unit) {
    if (target() is Container) {
      val messagesFixture = message(target() as Container, title, timeout)
      func(messagesFixture)
    }
    else throw UnsupportedOperationException(
      "Sorry, unable to find PluginTable component with ${target().toString()} as a Container")

  }


  //*********FIXTURES METHODS FOR IDEFRAME WITHOUT ROBOT and TARGET

  /**
   * Context function for IdeFrame: get current editor and creates EditorFixture instance as receiver object. Code block after
   * it call methods on the receiver object (EditorFixture instance).
   */
  fun IdeFrameFixture.editor(func: EditorFixture.() -> Unit) {
    func(this.editor)
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

  /**
   * Extension function for IDE to iterate through the menu.
   *
   * @path items like: popup("New", "File")
   */
  fun IdeFrameFixture.popup(vararg path: String)
    = this.invokeMenuPath(*path)

  //*********COMMON FUNCTIONS WITHOUT CONTEXT
  /**
   * Type text by symbol with a constant delay. Generate system key events, so entered text will aply to a focused component.
   */
  fun typeText(text: String) = GuiTestUtil.typeText(text, myRobot, 10)

  /**
   * @param keyStroke should follow {@link KeyStrokeAdapter#getKeyStroke(String)} instructions and be generated by {@link KeyStrokeAdapter#toString(KeyStroke)} preferably
   *
   * examples: shortcut("meta comma"), shortcut("ctrl alt s"), shortcut("alt f11")
   * modifiers order: shift | ctrl | control | meta | alt | altGr | altGraph
   */
  fun shortcut(keyStroke: String) = GuiTestUtil.invokeActionViaShortcut(myRobot, keyStroke)

  /**
   * Invoke action by actionId through its keystroke
   */
  fun invokeAction(actionId: String) = GuiTestUtil.invokeAction(myRobot, actionId)

  /**
   * Take a screenshot for a specific component. Screenshot remain scaling and represent it in name of file.
   */
  fun screenshot(component: Component, screenshotName: String): Unit {

    val extension = "${getScaleSuffix()}.png"
    val pathWithTestFolder = pathToSaveScreenshots.path + File.separator + this.testName
    val fileWithTestFolder = File(pathWithTestFolder)
    FileUtil.ensureExists(fileWithTestFolder)
    var screenshotFilePath = File(fileWithTestFolder, screenshotName + extension)
    if (screenshotFilePath.isFile) {
      val format = SimpleDateFormat("MM-dd-yyyy.HH.mm.ss")
      val now = format.format(GregorianCalendar().time)
      screenshotFilePath = File(fileWithTestFolder, screenshotName + "." + now + extension)
    }
    screenshotTaker.saveComponentAsPng(component, screenshotFilePath.path)
    println(message = "Screenshot for a component \"${component.toString()}\" taken and stored at ${screenshotFilePath.path}")

  }


  /**
   * Finds JDialog with a specific title (if title is null showing dialog should be only one) and returns created JDialogFixture
   */
  fun dialog(title: String? = null, timeout: Long): JDialogFixture {
    if (title == null) {
      val jDialog = waitUntilFound(null, JDialog::class.java, timeout) { jDialog -> true }
      return JDialogFixture(myRobot, jDialog)
    }
    else {
      return JDialogFixture.find(myRobot, title, timeout.toFestTimeout())
    }
  }

  //*********PRIVATE WRAPPER FUNCTIONS FOR FIXTURES

  private fun Long.toFestTimeout(): Timeout = if (this == 0L) timeout(50, TimeUnit.MILLISECONDS) else timeout(this, TimeUnit.SECONDS)

  private fun message(container: Container, title: String, timeout: Long): MessagesFixture
    = MessagesFixture.findByTitle(myRobot, container, title, timeout.toFestTimeout())

  private fun jList(container: Container, containingItem: String? = null, timeout: Long): JListFixture {

    val extCellReader = ExtendedJListCellReader()
    val myJList = waitUntilFound(container, JList::class.java, timeout) { jList ->
      if (containingItem == null) true //if were searching for any jList()
      else {
        val elements = (0..jList.model.size - 1).map { it -> extCellReader.valueAt(jList, it) }
        elements.any { it.toString() == containingItem } && jList.isShowing
      }
    }
    val jListFixture = JListFixture(myRobot, myJList)
    jListFixture.replaceCellReader(extCellReader)
    return jListFixture
  }

  private fun button(container: Container, name: String, timeout: Long): ExtendedButtonFixture {
    val jButton = waitUntilFound(container, JButton::class.java, timeout) {
      it.flags {
        isShowing && isVisible && text == name
      }
    }
    return ExtendedButtonFixture(myRobot, jButton)
  }

  private fun componentWithBrowseButton(container: Container,
                                        foo: Class<out ComponentWithBrowseButton<out JComponent>>,
                                        timeout: Long): ComponentWithBrowseButtonFixture {
    val component = waitUntilFound(container, ComponentWithBrowseButton::class.java, timeout) {
      component ->
      component.isShowing && foo.isInstance(component)
    }
    return ComponentWithBrowseButtonFixture(component, myRobot)
  }

  private fun combobox(container: Container, text: String, timeout: Long): ComboBoxFixture {
    //wait until label has appeared
    waitUntilFound(container, Component::class.java,
                   timeout) { it.flags { isShowing && isTextComponent() && getComponentText() == text } }
    val comboBox = findBoundedComponentByText(myRobot, container, text, JComboBox::class.java)
    val comboboxFixture = ComboBoxFixture(myRobot, comboBox)
    comboboxFixture.replaceCellReader(ExtendedJComboboxCellReader())
    return comboboxFixture
  }

  private fun checkbox(container: Container, labelText: String, timeout: Long): CheckBoxFixture {
    //wait until label has appeared
    val jCheckBox = waitUntilFound(container, JCheckBox::class.java,
                                   timeout) { it.flags { isShowing && isVisible && text == labelText } }
    return CheckBoxFixture(myRobot, jCheckBox)
  }

  private fun actionLink(container: Container, name: String, timeout: Long) = ActionLinkFixture.findActionLinkByName(name, myRobot,
                                                                                                                     container,
                                                                                                                     timeout.toFestTimeout())

  private fun actionButton(container: Container, actionName: String, timeout: Long): ActionButtonFixture {
    try {
      return ActionButtonFixture.findByText(actionName, myRobot, container, timeout.toFestTimeout())
    }
    catch (componentLookupException: ComponentLookupException) {
      return ActionButtonFixture.findByActionId(actionName, myRobot, container, timeout.toFestTimeout())
    }
  }

  private fun actionButtonByClass(container: Container, actionClassName: String, timeout: Long): ActionButtonFixture =
    ActionButtonFixture.findByActionClassName(actionClassName, myRobot, container, timeout.toFestTimeout())

  private fun editor(ideFrameFixture: IdeFrameFixture, timeout: Long): EditorFixture = EditorFixture(myRobot, ideFrameFixture)

  private fun radioButton(container: Container, labelText: String, timeout: Long) = GuiTestUtil.findRadioButton(myRobot, container,
                                                                                                                labelText,
                                                                                                                timeout.toFestTimeout())

  private fun textfield(container: Container, labelText: String?, timeout: Long): JTextComponentFixture {
    //if 'textfield()' goes without label
    if (labelText.isNullOrEmpty()) {
      val jTextField = waitUntilFound(container, JTextField::class.java, timeout) { jTextField -> jTextField.isShowing }
      return JTextComponentFixture(myRobot, jTextField)
    }
    //wait until label has appeared
    waitUntilFound(container, Component::class.java, timeout) {
      it.flags { isShowing && isVisible && isTextComponent() && getComponentText() == labelText }
    }
    val jTextComponent = findBoundedComponentByText(myRobot, container, labelText!!, JTextComponent::class.java)
    return JTextComponentFixture(myRobot, jTextComponent)
  }

  private fun linkLabel(container: Container, labelText: String, timeout: Long): ComponentFixture<ComponentFixture<*, *>, LinkLabel<*>> {
    val myLinkLabel = waitUntilFound(myRobot, container, typeMatcher(LinkLabel::class.java) { it.isShowing && (it.text == labelText) },
                                     timeout.toFestTimeout())
    return ComponentFixture<ComponentFixture<*, *>, LinkLabel<*>>(ComponentFixture::class.java, myRobot, myLinkLabel)
  }

  private fun hyperlinkLabel(container: Container, labelText: String, timeout: Long): HyperlinkLabelFixture {
    val hyperlinkLabel = waitUntilFound(myRobot, container, typeMatcher(HyperlinkLabel::class.java) {
      (it.isShowing && (it.text == labelText))
    }, timeout.toFestTimeout())
    return HyperlinkLabelFixture(myRobot, hyperlinkLabel)
  }

  private fun table(container: Container, cellText: String, timeout: Long): JTableFixture {
    return waitUntilFoundFixture(container, JTable::class.java, timeout) {
      val jTableFixture = JTableFixture(myRobot, it)
      jTableFixture.replaceCellReader(ExtendedJTableCellReader())
      val hasCellWithText = try {
        jTableFixture.cell(cellText); true
      }
      catch (e: ActionFailedException) {
        false
      }
      Pair(hasCellWithText, jTableFixture)
    }
  }

  private fun pluginTable(container: Container, timeout: Long) = PluginTableFixture.find(myRobot, container, timeout.toFestTimeout())

  private fun popupClick(container: Container, itemName: String, timeout: Long) {
    GuiTestUtil.clickPopupMenuItem(itemName, false, container, myRobot, timeout.toFestTimeout())
  }

  private fun jTreePath(container: Container, timeout: Long, vararg pathStrings: String): ExtendedTreeFixture {
    val myTree: JTree?
    val pathList = pathStrings.toList()
    if (pathList.isEmpty()) {
      myTree = waitUntilFound(myRobot, container, typeMatcher(JTree::class.java) { true }, timeout.toFestTimeout())
    }
    else {
      myTree = waitUntilFound(myRobot, container, typeMatcher(JTree::class.java) { ExtendedTreeFixture(myRobot, it).hasPath(pathList) },
                              timeout.toFestTimeout())
    }
    val treeFixture: ExtendedTreeFixture = ExtendedTreeFixture(myRobot, myTree)
    return treeFixture
  }

  private fun checkboxTree(container: Container, timeout: Long, vararg pathStrings: String): CheckboxTreeFixture {
    val extendedTreeFixture = jTreePath(container, timeout, *pathStrings)
    if (extendedTreeFixture.tree !is CheckboxTree) throw ComponentLookupException("Found JTree but not a CheckboxTree")
    return CheckboxTreeFixture(myRobot, extendedTreeFixture.tree)
  }

  fun ComponentFixture<*, *>.exists(fixture: () -> AbstractComponentFixture<*, *, *>): Boolean {
    val tmp = defaultTimeout
    defaultTimeout = 0
    try {
      fixture.invoke()
      defaultTimeout = tmp
    }
    catch(ex: Exception) {
      when (ex) {
        is ComponentLookupException,
        is WaitTimedOutError -> {
          defaultTimeout = tmp; return false
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
  fun getScaleSuffix(): String? {
    val scaleEnabled: Boolean = (GuiTestUtil.getSystemPropertyOrEnvironmentVariable("sun.java2d.uiScale.enabled")?.toLowerCase().equals(
      "true"))
    if (!scaleEnabled) return ""
    val uiScaleVal = GuiTestUtil.getSystemPropertyOrEnvironmentVariable("sun.java2d.uiScale") ?: return ""
    return "@${uiScaleVal}x"
  }

  protected fun <ComponentType : Component> waitUntilFound(container: Container?,
                                                           componentClass: Class<ComponentType>,
                                                           timeout: Long,
                                                           matcher: (ComponentType) -> Boolean): ComponentType {
    return GuiTestUtil.waitUntilFound(myRobot, container, typeMatcher(componentClass) { matcher(it) }, timeout.toFestTimeout())
  }

  protected fun <Fixture, ComponentType : Component> waitUntilFoundFixture(container: Container?,
                                                                           componentClass: Class<ComponentType>,
                                                                           timeout: Long,
                                                                           matcher: (ComponentType) -> Pair<Boolean, Fixture>): Fixture {
    val ref = Ref<Fixture>()
    GuiTestUtil.waitUntilFound(myRobot, container, typeMatcher(componentClass)
    {
      val (matched, fixture) = matcher(it)
      if (matched) ref.set(fixture)
      matched
    }, timeout.toFestTimeout())
    return ref.get()
  }

  fun pause(condition: String = "Unspecified condition", timeoutSeconds: Long = 120, testFunction: () -> Boolean) {
    Pause.pause(object : Condition(condition) {
      override fun test() = testFunction()
    }, Timeout.timeout(timeoutSeconds, TimeUnit.SECONDS))
  }

  private fun <ComponentType : Component?> typeMatcher(componentTypeClass: Class<ComponentType>,
                                                       matcher: (ComponentType) -> Boolean): GenericTypeMatcher<ComponentType> {
    return object : GenericTypeMatcher<ComponentType>(componentTypeClass) {
      override fun isMatching(component: ComponentType): Boolean = matcher(component)
    }
  }

  inline fun <ExtendingType> ExtendingType.flags(flagCheckFunction: ExtendingType.() -> Boolean): Boolean {
    with(this) {
      return flagCheckFunction()
    }
  }

}
