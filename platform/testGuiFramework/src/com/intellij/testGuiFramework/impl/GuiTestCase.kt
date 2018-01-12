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
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testGuiFramework.cellReader.ExtendedJComboboxCellReader
import com.intellij.testGuiFramework.cellReader.ExtendedJListCellReader
import com.intellij.testGuiFramework.cellReader.ExtendedJTableCellReader
import com.intellij.testGuiFramework.fixtures.*
import com.intellij.testGuiFramework.fixtures.extended.ExtendedButtonFixture
import com.intellij.testGuiFramework.fixtures.extended.ExtendedTreeFixture
import com.intellij.testGuiFramework.fixtures.newProjectWizard.NewProjectWizardFixture
import com.intellij.testGuiFramework.framework.GuiTestLocalRunner
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.GuiTestUtil.waitUntilFound
import com.intellij.testGuiFramework.framework.IdeTestApplication.getTestScreenshotDirPath
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.findBoundedComponentByText
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.getComponentText
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.isTextComponent
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.typeMatcher
import com.intellij.testGuiFramework.launcher.system.SystemInfo
import com.intellij.testGuiFramework.launcher.system.SystemInfo.isMac
import com.intellij.testGuiFramework.util.Clipboard
import com.intellij.testGuiFramework.util.Key
import com.intellij.testGuiFramework.util.Shortcut
import com.intellij.ui.CheckboxTree
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.AsyncProcessIcon
import org.fest.swing.exception.ActionFailedException
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.*
import org.fest.swing.image.ScreenshotTaker
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import org.fest.swing.timing.Timeout.timeout
import org.junit.Rule
import org.junit.runner.RunWith
import java.awt.Component
import java.awt.Container
import java.io.File
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
@RunWith(GuiTestLocalRunner::class)
open class GuiTestCase {

  @Rule
  @JvmField
  val guiTestRule = GuiTestRule()

  /**
   * default timeout to find target component for fixture. Using seconds as time unit.
   */
  var defaultTimeout = 120L

  val settingsTitle: String = if (isMac()) "Preferences" else "Settings"
  val defaultSettingsTitle: String = if (isMac()) "Default Preferences" else "Default Settings"
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

  //*********FIXTURES METHODS WITHOUT ROBOT and TARGET; KOTLIN ONLY
  /**
   * Finds a JList component in hierarchy of context component with a containingItem and returns JListFixture.
   *
   * @timeout in seconds to find JList component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.jList(containingItem: String? = null, timeout: Long = defaultTimeout): JListFixture =
    if (target() is Container) {
      val extCellReader = ExtendedJListCellReader()
      val myJList = waitUntilFound(target() as Container, JList::class.java, timeout) { jList ->
        if (containingItem == null) true //if were searching for any jList()
        else {
          val elements = (0 until jList.model.size).map { it -> extCellReader.valueAt(jList, it) }
          elements.any { it.toString() == containingItem } && jList.isShowing
        }
      }
      val jListFixture = JListFixture(guiTestRule.robot(), myJList)
      jListFixture.replaceCellReader(extCellReader)
      jListFixture
    }
    else throw unableToFindComponent("JList")

  /**
   * Finds a JButton component in hierarchy of context component with a name and returns ExtendedButtonFixture.
   *
   * @timeout in seconds to find JButton component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.button(name: String, timeout: Long = defaultTimeout): ExtendedButtonFixture =
    if (target() is Container) {
      val jButton = waitUntilFound(target() as Container, JButton::class.java, timeout) {
        it.isShowing && it.isVisible && it.text == name
      }
      ExtendedButtonFixture(guiTestRule.robot(), jButton)
    }
    else throw unableToFindComponent("""JButton named by $name""")

  fun <S, C : Component> ComponentFixture<S, C>.componentWithBrowseButton(boundedLabelText: String,
                                                                          timeout: Long = defaultTimeout): ComponentWithBrowseButtonFixture {
    if (target() is Container) {
      val boundedLabel = waitUntilFound(target() as Container, JLabel::class.java, timeout) {
        it.text == boundedLabelText && it.isShowing
      }
      val component = boundedLabel.labelFor
      if (component is ComponentWithBrowseButton<*>) {
        return ComponentWithBrowseButtonFixture(component, guiTestRule.robot())
      }
    }
    throw unableToFindComponent("ComponentWithBrowseButton with labelFor=$boundedLabelText")
  }

  fun <S, C : Component> ComponentFixture<S, C>.treeTable(timeout: Long = defaultTimeout): TreeTableFixture {
    if (target() is Container) {
      val table = waitUntilFound(guiTestRule.robot(), target() as Container,
                                 typeMatcher(TreeTable::class.java) { true },
                                 timeout.toFestTimeout()
      )
      return TreeTableFixture(guiTestRule.robot(), table)
    }
    else throw UnsupportedOperationException(
      "Sorry, unable to find inspections tree with ${target()} as a Container")
  }

  fun <S, C : Component> ComponentFixture<S, C>.spinner(boundedLabelText: String, timeout: Long = defaultTimeout): JSpinnerFixture {
    if (target() is Container) {
      val boundedLabel = waitUntilFound(target() as Container, JLabel::class.java, timeout) { it.text == boundedLabelText }
      val component = boundedLabel.labelFor
      if (component is JSpinner)
        return JSpinnerFixture(guiTestRule.robot(), component)
    }
    throw unableToFindComponent("""JSpinner with $boundedLabelText bounded label""")
  }

  /**
   * Finds a JComboBox component in hierarchy of context component by text of label and returns ComboBoxFixture.
   *
   * @timeout in seconds to find JComboBox component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.combobox(labelText: String, timeout: Long = defaultTimeout): ComboBoxFixture =
    if (target() is Container) {
      try {
        waitUntilFound(target() as Container, Component::class.java,
                       timeout) { it.isShowing && it.isTextComponent() && it.getComponentText() == labelText }
      }
      catch (e: WaitTimedOutError) {
        throw ComponentLookupException("Unable to find label for a combobox with text \"$labelText\" in $timeout seconds")
      }
      val comboBox = findBoundedComponentByText(guiTestRule.robot(), target() as Container, labelText, JComboBox::class.java)
      val comboboxFixture = ComboBoxFixture(guiTestRule.robot(), comboBox)
      comboboxFixture.replaceCellReader(ExtendedJComboboxCellReader())
      comboboxFixture
    }
    else throw unableToFindComponent("""JComboBox near label by "$labelText"""")


  /**
   * Finds a JCheckBox component in hierarchy of context component by text of label and returns CheckBoxFixture.
   *
   * @timeout in seconds to find JCheckBox component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.checkbox(labelText: String, timeout: Long = defaultTimeout): CheckBoxFixture =
    if (target() is Container) {
      val jCheckBox = waitUntilFound(target() as Container, JCheckBox::class.java, timeout) {
        it.isShowing && it.isVisible && it.text == labelText
      }
      CheckBoxFixture(guiTestRule.robot(), jCheckBox)
    }
    else throw unableToFindComponent("""JCheckBox label by "$labelText""")

  /**
   * Finds a ActionLink component in hierarchy of context component by name and returns ActionLinkFixture.
   *
   * @timeout in seconds to find ActionLink component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.actionLink(name: String, timeout: Long = defaultTimeout): ActionLinkFixture =
    if (target() is Container) {
      ActionLinkFixture.findActionLinkByName(name, guiTestRule.robot(), target() as Container, timeout.toFestTimeout())
    }
    else throw unableToFindComponent("""ActionLink by name "$name"""")

  /**
   * Finds a ActionButton component in hierarchy of context component by action name and returns ActionButtonFixture.
   *
   * @actionName text or action id of an action button (@see com.intellij.openapi.actionSystem.ActionManager#getId())
   * @timeout in seconds to find ActionButton component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.actionButton(actionName: String, timeout: Long = defaultTimeout): ActionButtonFixture =
    if (target() is Container) {
      try {
        ActionButtonFixture.findByText(actionName, guiTestRule.robot(), target() as Container, timeout.toFestTimeout())
      }
      catch (componentLookupException: ComponentLookupException) {
        ActionButtonFixture.findByActionId(actionName, guiTestRule.robot(), target() as Container, timeout.toFestTimeout())
      }
    }
    else throw unableToFindComponent("""ActionButton by action name "$actionName"""")

  /**
   * Finds a ActionButton component in hierarchy of context component by action class name and returns ActionButtonFixture.
   *
   * @actionClassName qualified name of class for action
   * @timeout in seconds to find ActionButton component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.actionButtonByClass(actionClassName: String,
                                                                    timeout: Long = defaultTimeout): ActionButtonFixture =
    if (target() is Container) {
      ActionButtonFixture.findByActionClassName(actionClassName, guiTestRule.robot(), target() as Container, timeout.toFestTimeout())
    }
    else throw unableToFindComponent("""ActionButton by action class name "$actionClassName"""")

  /**
   * Finds a JRadioButton component in hierarchy of context component by label text and returns JRadioButtonFixture.
   *
   * @timeout in seconds to find JRadioButton component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.radioButton(textLabel: String, timeout: Long = defaultTimeout): RadioButtonFixture =
    if (target() is Container) GuiTestUtil.findRadioButton(guiTestRule.robot(), target() as Container, textLabel, timeout.toFestTimeout())
    else throw unableToFindComponent("""RadioButton by label "$textLabel"""")

  /**
   * Finds a JTextComponent component (JTextField) in hierarchy of context component by text of label and returns JTextComponentFixture.
   *
   * @textLabel could be a null if label is absent
   * @timeout in seconds to find JTextComponent component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.textfield(textLabel: String?, timeout: Long = defaultTimeout): JTextComponentFixture {
    if (target() is Container) {
      val container = target() as Container
      if (textLabel.isNullOrEmpty()) {
        val jTextField = waitUntilFound(container, JTextField::class.java, timeout) { jTextField -> jTextField.isShowing }
        return JTextComponentFixture(guiTestRule.robot(), jTextField)
      }
      //wait until label has appeared
      waitUntilFound(container, Component::class.java, timeout) {
        it.isShowing && it.isVisible && it.isTextComponent() && it.getComponentText() == textLabel
      }
      val jTextComponent = findBoundedComponentByText(guiTestRule.robot(), container, textLabel!!, JTextComponent::class.java)
      return JTextComponentFixture(guiTestRule.robot(), jTextComponent)
    }
    else throw unableToFindComponent("""JTextComponent (JTextField) by label "$textLabel"""")
  }

  /**
   * Finds a JTree component in hierarchy of context component by a path and returns ExtendedTreeFixture.
   *
   * @pathStrings comma separated array of Strings, representing path items: jTree("myProject", "src", "Main.java")
   * @timeout in seconds to find JTree component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.jTree(vararg pathStrings: String, timeout: Long = defaultTimeout): ExtendedTreeFixture =
    if (target() is Container) jTreePath(target() as Container, timeout, *pathStrings)
    else throw unableToFindComponent("""JTree "${if (pathStrings.isNotEmpty()) "by path $pathStrings" else ""}"""")

  /**
   * Finds a CheckboxTree component in hierarchy of context component by a path and returns CheckboxTreeFixture.
   *
   * @pathStrings comma separated array of Strings, representing path items: checkboxTree("JBoss", "JBoss Drools")
   * @timeout in seconds to find JTree component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.checkboxTree(vararg pathStrings: String,
                                                             timeout: Long = defaultTimeout): CheckboxTreeFixture =
    if (target() is Container) {
      val extendedTreeFixture = jTreePath(target() as Container, timeout, *pathStrings)
      if (extendedTreeFixture.tree !is CheckboxTree) throw ComponentLookupException("Found JTree but not a CheckboxTree")
      CheckboxTreeFixture(guiTestRule.robot(), extendedTreeFixture.tree)
    }
    else throw unableToFindComponent("""CheckboxTree "${if (pathStrings.isNotEmpty()) "by path $pathStrings" else ""}"""")

  /**
   * Finds a JTable component in hierarchy of context component by a cellText and returns JTableFixture.
   *
   * @timeout in seconds to find JTable component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.table(cellText: String, timeout: Long = defaultTimeout): JTableFixture =
    if (target() is Container) {
      val jTable = waitUntilFound(target() as Container, JTable::class.java, timeout) {
        val jTableFixture = JTableFixture(guiTestRule.robot(), it)
        jTableFixture.replaceCellReader(ExtendedJTableCellReader())
        try {
          jTableFixture.cell(cellText)
          true
        }
        catch (e: ActionFailedException) {
          false
        }
      }
      JTableFixture(guiTestRule.robot(), jTable)
    }
    else throw unableToFindComponent("""JTable with cell text "$cellText"""")

  /**
   * Finds popup on screen with item (itemName) and clicks on it item
   *
   * @timeout timeout in seconds to find JTextComponent component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.popupClick(itemName: String, timeout: Long = defaultTimeout) =
    if (target() is Container) {
      JBListPopupFixture.clickPopupMenuItem(itemName, false, target() as Container, guiTestRule.robot(), timeout.toFestTimeout())
    }
    else throw unableToFindComponent("Popup")

  /**
   * Finds a LinkLabel component in hierarchy of context component by a link name and returns fixture for it.
   *
   * @timeout in seconds to find LinkLabel component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.linkLabel(linkName: String, timeout: Long = defaultTimeout) =
    if (target() is Container) {
      val myLinkLabel = waitUntilFound(
        guiTestRule.robot(), target() as Container,
        typeMatcher(LinkLabel::class.java) { it.isShowing && (it.text == linkName) },
        timeout.toFestTimeout())
      ComponentFixture(ComponentFixture::class.java, guiTestRule.robot(), myLinkLabel)
    }
    else throw unableToFindComponent("LinkLabel")


  fun <S, C : Component> ComponentFixture<S, C>.hyperlinkLabel(labelText: String, timeout: Long = defaultTimeout): HyperlinkLabelFixture =
    if (target() is Container) {
      val hyperlinkLabel = waitUntilFound(guiTestRule.robot(), target() as Container, typeMatcher(HyperlinkLabel::class.java) {
        it.isShowing && (it.text == labelText)
      }, timeout.toFestTimeout())
      HyperlinkLabelFixture(guiTestRule.robot(), hyperlinkLabel)
    }
    else throw unableToFindComponent("""HyperlinkLabel by label text: "$labelText"""")

  /**
   * Finds a table of plugins component in hierarchy of context component by a link name and returns fixture for it.
   *
   * @timeout in seconds to find table of plugins component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.pluginTable(timeout: Long = defaultTimeout) =
    if (target() is Container) PluginTableFixture.find(guiTestRule.robot(), target() as Container, timeout.toFestTimeout())
    else throw unableToFindComponent("PluginTable")

  /**
   * Finds a Message component in hierarchy of context component by a title MessageFixture.
   *
   * @timeout in seconds to find component for Message
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.message(title: String, timeout: Long = defaultTimeout) =
    if (target() is Container) MessagesFixture.findByTitle(guiTestRule.robot(), target() as Container, title, timeout.toFestTimeout())
    else throw unableToFindComponent("Message")


  /**
   * Finds a Message component in hierarchy of context component by a title MessageFixture.
   *
   * @timeout in seconds to find component for Message
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.message(title: String, timeout: Long = defaultTimeout, func: MessagesFixture.() -> Unit) {
    if (target() is Container) func(MessagesFixture.findByTitle(guiTestRule.robot(), target() as Container, title, timeout.toFestTimeout()))
    else throw unableToFindComponent("Message")
  }

  /**
   * Finds a JBLabel component in hierarchy of context component by a label name and returns fixture for it.
   *
   * @timeout in seconds to find JBLabel component
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <S, C : Component> ComponentFixture<S, C>.label(labelName: String, timeout: Long = defaultTimeout): JLabelFixture =
    if (target() is Container) {
      val jbLabel = waitUntilFound(
        guiTestRule.robot(), target() as Container,
        typeMatcher(JBLabel::class.java) { it.isShowing && (it.text == labelName || labelName in it.text) },
        timeout.toFestTimeout())
      JLabelFixture(guiTestRule.robot(), jbLabel)
    }
    else throw unableToFindComponent("JBLabel")

  private fun <S, C : Component> ComponentFixture<S, C>.unableToFindComponent(component: String): ComponentLookupException =
    ComponentLookupException("""Sorry, unable to find $component component with ${target()} as a Container""")

  /**
   * Find an AsyncProcessIcon component in a current context (gets by receiver) and returns a fixture for it.
   *
   * @timeout timeout in seconds to find AsyncProcessIcon
   */
  fun <S, C : Component> ComponentFixture<S, C>.asyncProcessIcon(timeout: Long = defaultTimeout): AsyncProcessIconFixture {
    val asyncProcessIcon = waitUntilFound(
      guiTestRule.robot(),
      target() as Container,
      typeMatcher(AsyncProcessIcon::class.java) { it.isShowing && it.isVisible },
      timeout.toFestTimeout())
    return AsyncProcessIconFixture(guiTestRule.robot(), asyncProcessIcon)
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
  fun IdeFrameFixture.editor(tabName: String, func: EditorFixture.() -> Unit) {
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

  /**
   * Extension function for IDE to iterate through the menu.
   *
   * @path items like: popup("New", "File")
   */
  fun IdeFrameFixture.popup(vararg path: String)
    = this.invokeMenuPath(*path)


  fun CustomToolWindowFixture.ContentFixture.editor(func: EditorFixture.() -> Unit) {
    func(this.editor())
  }

  //*********COMMON FUNCTIONS WITHOUT CONTEXT
  /**
   * Type text by symbol with a constant delay. Generate system key events, so entered text will aply to a focused component.
   */
  fun typeText(text: String) = GuiTestUtil.typeText(text, guiTestRule.robot(), 10)

  /**
   * @param keyStroke should follow {@link KeyStrokeAdapter#getKeyStroke(String)} instructions and be generated by {@link KeyStrokeAdapter#toString(KeyStroke)} preferably
   *
   * examples: shortcut("meta comma"), shortcut("ctrl alt s"), shortcut("alt f11")
   * modifiers order: shift | ctrl | control | meta | alt | altGr | altGraph
   */
  fun shortcut(keyStroke: String) = GuiTestUtil.invokeActionViaShortcut(guiTestRule.robot(), keyStroke)

  fun shortcut(shortcut: Shortcut) = shortcut(shortcut.getKeystroke())

  fun shortcut(key: Key) = shortcut(key.name)

  /**
   * copies a given string to a system clipboard
   */
  fun copyToClipboard(string: String) = Clipboard.copyToClipboard(string)

  /**
   * Invoke action by actionId through its keystroke
   */
  fun invokeAction(actionId: String) = GuiTestUtil.invokeAction(guiTestRule.robot(), actionId)

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
      screenshotFilePath = File(fileWithTestFolder, screenshotName + "." + now + extension)
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
      return JDialogFixture(guiTestRule.robot(), jDialog)
    }
    else {
      try {
        val dialog = GuiTestUtilKt.withPauseWhenNull(timeoutInSeconds.toInt()) {
          val allMatchedDialogs = guiTestRule.robot().finder().findAll(typeMatcher(JDialog::class.java) {
            if (ignoreCaseTitle) it.title.toLowerCase() == title.toLowerCase() else it.title == title
          }).filter { it.isShowing && it.isEnabled && it.isVisible }
          if (allMatchedDialogs.size > 1) throw Exception(
            "Found more than one (${allMatchedDialogs.size}) dialogs matched title \"$title\"")
          allMatchedDialogs.firstOrNull()
        }
        return JDialogFixture(guiTestRule.robot(), dialog)
      }
      catch (timeoutError: WaitTimedOutError) {
        throw ComponentLookupException("Timeout error for finding JDialog by title \"$title\" for $timeoutInSeconds seconds")
      }
    }
  }

  private fun Long.toFestTimeout(): Timeout = if (this == 0L) timeout(50, TimeUnit.MILLISECONDS) else timeout(this, TimeUnit.SECONDS)


  private fun jTreePath(container: Container, timeout: Long, vararg pathStrings: String): ExtendedTreeFixture {
    val myTree: JTree?
    val pathList = pathStrings.toList()
    myTree = if (pathList.isEmpty()) {
      waitUntilFound(guiTestRule.robot(), container, typeMatcher(JTree::class.java) { true }, timeout.toFestTimeout())
    }
    else {
      waitUntilFound(guiTestRule.robot(), container,
                     typeMatcher(JTree::class.java) { ExtendedTreeFixture(guiTestRule.robot(), it).hasPath(pathList) },
                     timeout.toFestTimeout())
    }
    return ExtendedTreeFixture(guiTestRule.robot(), myTree)
  }

  fun exists(fixture: () -> AbstractComponentFixture<*, *, *>): Boolean {
    val tmp = defaultTimeout
    defaultTimeout = 0
    try {
      fixture.invoke()
      defaultTimeout = tmp
    }
    catch (ex: Exception) {
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
  private fun getScaleSuffix(): String? {
    val scaleEnabled: Boolean = (GuiTestUtil.getSystemPropertyOrEnvironmentVariable("sun.java2d.uiScale.enabled")?.toLowerCase().equals(
      "true"))
    if (!scaleEnabled) return ""
    val uiScaleVal = GuiTestUtil.getSystemPropertyOrEnvironmentVariable("sun.java2d.uiScale") ?: return ""
    return "@${uiScaleVal}x"
  }

  fun <ComponentType : Component> waitUntilFound(container: Container?,
                                                 componentClass: Class<ComponentType>,
                                                 timeout: Long,
                                                 matcher: (ComponentType) -> Boolean): ComponentType {
    return GuiTestUtil.waitUntilFound(guiTestRule.robot(), container, typeMatcher(componentClass) { matcher(it) }, timeout.toFestTimeout())
  }

  fun pause(condition: String = "Unspecified condition", timeoutSeconds: Long = 120, testFunction: () -> Boolean) {
    Pause.pause(object : Condition(condition) {
      override fun test() = testFunction()
    }, Timeout.timeout(timeoutSeconds, TimeUnit.SECONDS))
  }
}
