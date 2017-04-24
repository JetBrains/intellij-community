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
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testGuiFramework.cellReader.ExtendedJListCellReader
import com.intellij.testGuiFramework.cellReader.SettingsTreeCellReader
import com.intellij.testGuiFramework.fixtures.*
import com.intellij.testGuiFramework.fixtures.newProjectWizard.NewProjectWizardFixture
import com.intellij.testGuiFramework.framework.GuiTestBase
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.GuiTestUtil.waitUntilFound
import com.intellij.testGuiFramework.framework.IdeTestApplication.getTestScreenshotDirPath
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.net.HttpConfigurable
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.SmartWaitRobot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.LocationUnavailableException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.*
import org.fest.swing.image.ScreenshotTaker
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

  //*********CONTEXT FUNCTIONS ON LAMBDA RECEIVERS
  fun welcomeFrame(func: WelcomeFrameFixture.() -> Unit) {
    func.invoke(welcomeFrame())
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

  fun <S, C : Component> ComponentFixture<S, C>.jTree(path: String? = null, /*timeout in seconds*/
                                                      timeout: Long = defaultTimeout): JTreeFixture = if (target() is Container) jTree(
    target() as Container, path, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find JTree component \"${if (path != null) "by path ${path}" else ""}\" with ${target().toString()} as a Container")

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

  fun <S, C : Component> ComponentFixture<S, C>.message(title: String, /*timeout in seconds*/ timeout: Long = defaultTimeout) = if (target() is Container) message(target() as Container, title, timeout)
  else throw UnsupportedOperationException(
    "Sorry, unable to find PluginTable component with ${target().toString()} as a Container")

  //*********FIXTURES METHODS FOR IDEFRAME WITHOUT ROBOT and TARGET; KOTLIN ONLY
  fun IdeFrameFixture.editor(/*timeout in seconds*/ timeout: Long = defaultTimeout, func: EditorFixture.() -> Unit) {
    func.invoke(this.editor)
  }

  fun IdeFrameFixture.popup(vararg path: String) = this.invokeMenuPath(*path)

  //*********COMMON FUNCTIONS WITHOUT CONTEXT
  fun typeText(text: String) = GuiTestUtil.typeText(text, myRobot, 10)

  fun shortcut(keyStroke: String) = GuiTestUtil.invokeActionViaShortcut(myRobot, keyStroke)
  fun screenshot(component: Component, screenshotName: String): Unit {

    val extension = "${getScaleSuffix()}.png"
    val pathWithTestFolder = pathToSaveScreenshots.path + File.separator + this.testName
    val fileWithTestFolder = File(pathWithTestFolder)
    FileUtil.ensureExists(fileWithTestFolder)
    var screenshotFilePath = File(fileWithTestFolder, screenshotName + extension)
    if (screenshotFilePath.isFile) {
      val format = SimpleDateFormat("MM-dd-yyyy.HH:mm:ss")
      val now = format.format(GregorianCalendar().time)
      screenshotFilePath = File(fileWithTestFolder, screenshotName + "." + now + extension)
    }
    screenshotTaker.saveComponentAsPng(component, screenshotFilePath.path)
    println(message = "Screenshot for a component \"${component.toString()}\" taken and stored at ${screenshotFilePath.path}")

  }

  fun ideFrame() = findIdeFrame()!!
  fun welcomeFrame() = findWelcomeFrame()

  private fun Long.toFestTimeout(): Timeout = if (this == 0L) timeout(50, TimeUnit.MILLISECONDS) else timeout(this, TimeUnit.SECONDS)

  fun dialog(title: String? = null, timeout: Long): JDialogFixture {
    if (title == null) {
      val jDialog = waitUntilFound(/*robot*/ myRobot,
        /*root*/ null,
        /*matcher*/ object : GenericTypeMatcher<JDialog>(JDialog::class.java) {
        override fun isMatching(p0: JDialog): Boolean = true
      },
        /*timeout*/ timeout.toFestTimeout())
      return JDialogFixture(myRobot, jDialog)
    }
    else {
      return JDialogFixture.find(myRobot, title, timeout.toFestTimeout())
    }
  }

  private fun message(container: Container, title: String, timeout: Long): MessagesFixture  = MessagesFixture.findByTitle(myRobot, container, title, timeout.toFestTimeout())

  private fun jList(container: Container, containingItem: String? = null, timeout: Long): JListFixture {

    val extCellReader = ExtendedJListCellReader()
    val myJList = waitUntilFound(myRobot, container, object : GenericTypeMatcher<JList<*>>(JList::class.java) {
      override fun isMatching(myList: JList<*>): Boolean {
        if (containingItem == null) return true //if were searching for any jList()
        val elements = (0..myList.model.size - 1).map { it -> extCellReader.valueAt(myList, it) }
        return elements.any { it.toString() == containingItem }
      }
    }, timeout.toFestTimeout())
    val jListFixture = JListFixture(myRobot, myJList)
    jListFixture.replaceCellReader(extCellReader)
    return jListFixture
  }

  private fun button(container: Container, name: String, timeout: Long): JButtonFixture {
    val jButton = GuiTestUtil.waitUntilFound(myRobot, container, object : GenericTypeMatcher<JButton>(JButton::class.java) {
      override fun isMatching(jButton: JButton): Boolean = (jButton.isShowing && jButton.text == name)
    }, timeout.toFestTimeout())

    return JButtonFixture(myRobot, jButton)
  }

  private fun combobox(container: Container, labelText: String, timeout: Long): JComboBoxFixture {
    //wait until label has appeared
    GuiTestUtil.waitUntilFound(myRobot, container, object : GenericTypeMatcher<JLabel>(JLabel::class.java) {
      override fun isMatching(jLabel: JLabel): Boolean = (jLabel.isShowing && jLabel.text == labelText)
    }, timeout.toFestTimeout())

    return GuiTestUtil.findComboBox(myRobot, container, labelText)
  }

  private fun checkbox(container: Container, labelText: String, timeout: Long): CheckBoxFixture {
    //wait until label has appeared
    GuiTestUtil.waitUntilFound(myRobot, container, object : GenericTypeMatcher<JCheckBox>(JCheckBox::class.java) {
      override fun isMatching(checkBox: JCheckBox): Boolean = (checkBox.isShowing && checkBox.text == labelText)
    }, timeout.toFestTimeout())

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

  private fun editor(ideFrameFixture: IdeFrameFixture, timeout: Long): EditorFixture = EditorFixture(myRobot, ideFrameFixture)

  private fun radioButton(container: Container, labelText: String, timeout: Long) = GuiTestUtil.findRadioButton(myRobot, container,
                                                                                                                labelText,
                                                                                                                timeout.toFestTimeout())

  private fun textfield(container: Container, labelText: String?, timeout: Long): JTextComponentFixture {
    //if 'textfield()' goes without label
    if (labelText.isNullOrEmpty()) {
      val jTextField = GuiTestUtil.waitUntilFound(myRobot, container, object : GenericTypeMatcher<JTextField>(JTextField::class.java) {
        override fun isMatching(jTextField: JTextField): Boolean = jTextField.isShowing
      }, timeout.toFestTimeout())
      return JTextComponentFixture(myRobot, jTextField)
    }

    val jLabel = GuiTestUtil.waitUntilFound(myRobot, container, object : GenericTypeMatcher<JLabel>(JLabel::class.java) {
      override fun isMatching(jLabel: JLabel): Boolean = (jLabel.isShowing && jLabel.text == labelText)
    }, timeout.toFestTimeout())
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

  private fun jTree(container: Container, path: String? = null, timeout: Long): JTreeFixture {
    val myTree: JTree?
    if (path == null) {
      myTree = waitUntilFound(myRobot, container, object : GenericTypeMatcher<JTree>(JTree::class.java) {
        override fun isMatching(p0: JTree) = true
      }, timeout.toFestTimeout())
    }
    else {
      myTree = waitUntilFound(myRobot, container, object : GenericTypeMatcher<JTree>(JTree::class.java) {
        override fun isMatching(p0: JTree): Boolean {
          try {
            JTreeFixture(myRobot, p0).node(path)
            return true
          }
          catch(locationUnavailableException: LocationUnavailableException) {
            return false
          }
        }
      }, timeout.toFestTimeout())
    }
    if (myTree.javaClass.name == "com.intellij.openapi.options.newEditor.SettingsTreeView\$MyTree") {
      //replace cellreader
      return JTreeFixture(myRobot, myTree).replaceCellReader(SettingsTreeCellReader())
    }
    else
      return JTreeFixture(myRobot, myTree)
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

  //necessary only for Windows
  fun getScaleSuffix(): String? {
    val scaleEnabled: Boolean = (GuiTestUtil.getSystemPropertyOrEnvironmentVariable("sun.java2d.uiScale.enabled")?.toLowerCase().equals(
      "true"))
    if (!scaleEnabled) return ""
    val uiScaleVal = GuiTestUtil.getSystemPropertyOrEnvironmentVariable("sun.java2d.uiScale") ?: throw Exception(
      "Error: Java property\"sun.java2d.uiScale.enabled\" is enabled but \"sun.java2d.uiScale\" is not defined. Please check your jdk properties and environment variables")
    return "@${uiScaleVal}x"
  }


}
