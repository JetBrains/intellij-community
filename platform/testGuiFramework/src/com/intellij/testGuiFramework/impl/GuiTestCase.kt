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
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testGuiFramework.cellReader.ExtendedJListCellReader
import com.intellij.testGuiFramework.fixtures.*
import com.intellij.testGuiFramework.fixtures.extended.ExtendedTreeFixture
import com.intellij.testGuiFramework.fixtures.newProjectWizard.NewProjectWizardFixture
import com.intellij.testGuiFramework.framework.GuiTestBase
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.GuiTestUtil.waitUntilFound
import com.intellij.testGuiFramework.framework.IdeTestApplication.getTestScreenshotDirPath
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.net.HttpConfigurable
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.SmartMediaRobot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.*
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
 * @author Sergey Karashevich
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

  val screenshotTaker = ScreenshotTaker()
  var pathToSaveScreenshots = getTestScreenshotDirPath()
  var defaultTimeout = 120L //timeout in seconds

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myRobot = SmartMediaRobot()
    GuiSettings.setUp()
  }

  @Throws(InvocationTargetException::class, InterruptedException::class)
  override fun tearDown() {
    super.tearDown()
  }

  companion object {
    val IS_UNDER_TEAMCITY = System.getenv("TEAMCITY_VERSION") != null
  }

  //*********CONTEXT FUNCTIONS ON LAMBDA RECEIVERS
  open fun welcomeFrame(func: WelcomeFrameFixture.() -> Unit) {
    func.invoke(findWelcomeFrame())
  }

  fun ideFrame(func: IdeFrameFixture.() -> Unit) {
    func.invoke(findIdeFrame())
  }

  fun dialog(title: String? = null, timeout: Long = defaultTimeout, func: JDialogFixture.() -> Unit) {
    func.invoke(dialog(title, timeout))
  }

  fun simpleProject(func: IdeFrameFixture.() -> Unit) {
    func.invoke(importSimpleProject())
  }

  fun projectWizard(func: NewProjectWizardFixture.() -> Unit) {
    func.invoke(findNewProjectWizard())
  }

  fun IdeFrameFixture.projectView(func: ProjectViewFixture.() -> Unit) {
    func.invoke(this.projectView)
  }

  fun IdeFrameFixture.toolwindow(id: String, func: CustomToolWindowFixture.() -> Unit) {
    func.invoke(CustomToolWindowFixture(id, this))
  }

  //*********FIXTURES METHODS WITHOUT ROBOT and TARGET; KOTLIN ONLY
  fun <S, C : Component> ComponentFixture<S, C>.jList(containingItem: String? = null, /*timeout in seconds*/
                                                      timeout: Long = defaultTimeout): JListFixture = if (target() is Container) jList(
    target() as Container, containingItem, timeout)
  else throw UnsupportedOperationException("Sorry, unable to find JList component with ${target().toString()} as a Container")

  fun <S, C : Component> ComponentFixture<S, C>.button(name: String, /*timeout in seconds*/
                                                       timeout: Long = defaultTimeout): JButtonFixture = if (target() is Container) button(
    target() as Container, name, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find JButton component named by \"${name}\" with ${target().toString()} as a Container")

  fun <S, C : Component> ComponentFixture<S, C>.combobox(labelText: String, /*timeout in seconds*/
                                                         timeout: Long = defaultTimeout): JComboBoxFixture = if (target() is Container) combobox(
    target() as Container, labelText, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find JComboBox component near label by \"${labelText}\" with ${target().toString()} as a Container")

  fun <S, C : Component> ComponentFixture<S, C>.checkbox(labelText: String, /*timeout in seconds*/
                                                         timeout: Long = defaultTimeout): CheckBoxFixture = if (target() is Container) checkbox(
    target() as Container, labelText, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find JCheckBox component near label by \"${labelText}\" with ${target().toString()} as a Container")

  fun <S, C : Component> ComponentFixture<S, C>.actionLink(name: String, /*timeout in seconds*/
                                                           timeout: Long = defaultTimeout): ActionLinkFixture = if (target() is Container) actionLink(
    target() as Container, name, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find ActionLink component by name \"${name}\" with ${target().toString()} as a Container")

  fun <S, C : Component> ComponentFixture<S, C>.actionButton(actionName: String, /*timeout in seconds*/
                                                             timeout: Long = defaultTimeout): ActionButtonFixture = if (target() is Container) actionButton(
    target() as Container, actionName, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find ActionButton component by action name \"${actionName}\" with ${target().toString()} as a Container")

  fun <S, C : Component> ComponentFixture<S, C>.actionButtonByClass(actionClassName: String, /*timeout in seconds*/
                                                             timeout: Long = defaultTimeout): ActionButtonFixture = if (target() is Container) actionButtonByClass(
    target() as Container, actionClassName, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find ActionButton component by action class name \"${actionClassName}\" with ${target().toString()} as a Container")

  fun <S, C : Component> ComponentFixture<S, C>.radioButton(textLabel: String, /*timeout in seconds*/
                                                            timeout: Long = defaultTimeout): JRadioButtonFixture = if (target() is Container) radioButton(
    target() as Container, textLabel, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find RadioButton component by label \"${textLabel}\" with ${target().toString()} as a Container")

  fun <S, C : Component> ComponentFixture<S, C>.textfield(textLabel: String?, /*timeout in seconds*/
                                                          timeout: Long = defaultTimeout): JTextComponentFixture = if (target() is Container) textfield(
    target() as Container, textLabel, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find JTextComponent (JTextField) component by label \"${textLabel}\" with ${target().toString()} as a Container")

  fun <S, C : Component> ComponentFixture<S, C>.jTree(vararg pathStrings: String, /*timeout in seconds*/
                                                      timeout: Long = defaultTimeout): ExtendedTreeFixture = if (target() is Container) jTreePath(
    target() as Container, timeout, *pathStrings)
  else throw UnsupportedOperationException(
    "Sorry, unable to find JTree component \"${if (pathStrings != null) "by path ${pathStrings}" else ""}\" with ${target().toString()} as a Container")

  fun <S, C : Component> ComponentFixture<S, C>.popupClick(itemName: String, /*timeout in seconds*/
                                                           timeout: Long = defaultTimeout) = if (target() is Container) popupClick(
    target() as Container, itemName, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find Popup component with ${target().toString()} as a Container")

  fun <S, C : Component> ComponentFixture<S, C>.linkLabel(itemName: String, /*timeout in seconds*/
                                                          timeout: Long = defaultTimeout) = if (target() is Container) linkLabel(
    target() as Container, itemName, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find LinkLabel component with ${target().toString()} as a Container")

  fun <S, C : Component> ComponentFixture<S, C>.pluginTable(/*timeout in seconds*/ timeout: Long = defaultTimeout) = if (target() is Container) pluginTable(
    target() as Container, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find PluginTable component with ${target().toString()} as a Container")

  fun <S, C : Component> ComponentFixture<S, C>.message(title: String, /*timeout in seconds*/
                                                        timeout: Long = defaultTimeout) = if (target() is Container) message(
    target() as Container, title, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find PluginTable component with ${target().toString()} as a Container")

  //*********FIXTURES METHODS FOR IDEFRAME WITHOUT ROBOT and TARGET; KOTLIN ONLY
  fun IdeFrameFixture.editor(/*timeout in seconds*/ timeout: Long = defaultTimeout, func: EditorFixture.() -> Unit) {
    func.invoke(this.editor)
  }

  fun IdeFrameFixture.popup(vararg path: String) = this.invokeMenuPath(*path)

  //*********COMMON FUNCTIONS WITHOUT CONTEXT
  fun typeText(text: String) = GuiTestUtil.typeText(text, myRobot, 40)

  fun shortcut(keyStroke: String) = GuiTestUtil.invokeActionViaShortcut(myRobot, keyStroke)
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

  protected fun Long.toFestTimeout(): Timeout = if (this == 0L) timeout(50, TimeUnit.MILLISECONDS) else timeout(this, TimeUnit.SECONDS)

  fun dialog(title: String? = null, timeout: Long): JDialogFixture {
    if (title == null) {
      val jDialog = waitUntilFound(null, JDialog::class.java, timeout) { jDialog -> true }
      return JDialogFixture(myRobot, jDialog)
    }
    else {
      return JDialogFixture.find(myRobot, title, timeout.toFestTimeout())
    }
  }

  private fun message(container: Container, title: String, timeout: Long): MessagesFixture = MessagesFixture.findByTitle(myRobot, container,
                                                                                                                         title,
                                                                                                                         timeout.toFestTimeout())

  private fun jList(container: Container, containingItem: String? = null, timeout: Long): JListFixture {

    val extCellReader = ExtendedJListCellReader()
    val myJList = waitUntilFound(container, JList::class.java, timeout) { jList ->
      if (containingItem == null) true //if were searching for any jList()
      else {
        val elements = (0..jList.model.size - 1).map { it -> extCellReader.valueAt(jList, it) }
        elements.any { it.toString() == containingItem }
      }
    }
    val jListFixture = JListFixture(myRobot, myJList)
    jListFixture.replaceCellReader(extCellReader)
    return jListFixture
  }

  private fun button(container: Container, name: String, timeout: Long): JButtonFixture {
    val jButton = waitUntilFound(container, JButton::class.java, timeout) { jButton -> jButton.isShowing && jButton.text == name }
    return JButtonFixture(myRobot, jButton)
  }

  private fun combobox(container: Container, labelText: String, timeout: Long): JComboBoxFixture {
    //wait until label has appeared
    waitUntilFound(container, JLabel::class.java, timeout) { jLabel -> jLabel.isShowing && jLabel.text == labelText }
    return GuiTestUtil.findComboBox(myRobot, container, labelText)
  }

  private fun checkbox(container: Container, labelText: String, timeout: Long): CheckBoxFixture {
    //wait until label has appeared
    waitUntilFound(container, JCheckBox::class.java, timeout) { checkBox -> checkBox.isShowing && checkBox.text == labelText }
    return CheckBoxFixture.findByText(labelText, container, myRobot, false)
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

    val jLabel = waitUntilFound(container, JLabel::class.java, timeout) { jLabel -> jLabel.isShowing && jLabel.text == labelText }
    if (jLabel.labelFor != null && jLabel.labelFor is TextFieldWithBrowseButton)
      return JTextComponentFixture(myRobot, (jLabel.labelFor as TextFieldWithBrowseButton).textField)
    else
      return JTextComponentFixture(myRobot, myRobot.finder().findByLabel(labelText, JTextComponent::class.java))
  }

  private fun linkLabel(container: Container, labelText: String, timeout: Long): ComponentFixture<ComponentFixture<*, *>, LinkLabel<*>> {
    val myLinkLabel = waitUntilFound(myRobot, container, object : GenericTypeMatcher<LinkLabel<*>>(LinkLabel::class.java) {
      override fun isMatching(someLinkLabel: LinkLabel<*>) = (someLinkLabel.isShowing && (someLinkLabel.text == labelText))
    }, timeout.toFestTimeout())
    return ComponentFixture<ComponentFixture<*, *>, LinkLabel<*>>(ComponentFixture::class.java, myRobot, myLinkLabel)
  }

  private fun pluginTable(container: Container, timeout: Long) = PluginTableFixture.find(myRobot, container, timeout.toFestTimeout())

  private fun popupClick(container: Container, itemName: String, timeout: Long) {
    GuiTestUtil.clickPopupMenuItem(itemName, false, container, myRobot, timeout.toFestTimeout())
  }

  private fun jTreePath(container: Container, timeout: Long, vararg pathStrings: String): ExtendedTreeFixture {
    val myTree: JTree?
    val pathList = pathStrings.toList()
    if (pathList.isEmpty()) {
      myTree = waitUntilFound(myRobot, container, object : GenericTypeMatcher<JTree>(JTree::class.java) {
        override fun isMatching(tree: JTree) = true
      }, timeout.toFestTimeout())
    }
    else {
      myTree = waitUntilFound(myRobot, container, object : GenericTypeMatcher<JTree>(JTree::class.java) {
        override fun isMatching(tree: JTree): Boolean = ExtendedTreeFixture(myRobot, tree).hasPath(pathList)
      }, timeout.toFestTimeout())
    }
    val treeFixture: ExtendedTreeFixture = ExtendedTreeFixture(myRobot, myTree)
    return treeFixture
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

  fun <ReturnType> withTimeout(findComponent: () -> ReturnType): ReturnType {
    val result = Ref<ReturnType>()
    Pause.pause(object : Condition("Wait component to find with timeout: $defaultTimeout(s)") {
      override fun test(): Boolean {
        try {
          result.set(findComponent())
          return true
        }
        catch (e: ComponentLookupException) {
          return false
        }
      }
    }, defaultTimeout.toFestTimeout())
    return result.get()
  }

  protected fun <ComponentType : Component> waitUntilFound(container: Container?,
                                                         componentClass: Class<ComponentType>,
                                                         timeout: Long,
                                                         matcher: (ComponentType) -> Boolean): ComponentType {
    return GuiTestUtil.waitUntilFound(myRobot, container, object : GenericTypeMatcher<ComponentType>(componentClass) {
      override fun isMatching(cmp: ComponentType): Boolean = matcher(cmp)
    }, timeout.toFestTimeout())
  }


}
