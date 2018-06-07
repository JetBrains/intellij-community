// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.testGuiFramework.cellReader.ExtendedJComboboxCellReader
import com.intellij.testGuiFramework.cellReader.ExtendedJListCellReader
import com.intellij.testGuiFramework.fixtures.*
import com.intellij.testGuiFramework.fixtures.extended.ExtendedButtonFixture
import com.intellij.testGuiFramework.fixtures.extended.ExtendedTableFixture
import com.intellij.testGuiFramework.fixtures.extended.ExtendedTreeFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.GuiTestUtil.defaultTimeout
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.getComponentText
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.isTextComponent
import com.intellij.ui.CheckboxTree
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.AsyncProcessIcon
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.JLabelFixture
import org.fest.swing.fixture.JListFixture
import org.fest.swing.fixture.JSpinnerFixture
import org.fest.swing.fixture.JTextComponentFixture
import org.fest.swing.timing.Timeout
import java.awt.Component
import java.awt.Container
import java.util.concurrent.TimeUnit
import javax.swing.*


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
    val myJList = waitUntilFound(target() as Container, JList::class.java, timeout) { jList: JList<*> ->
      if (containingItem == null) true //if were searching for any jList()
      else {
        val elements = (0 until jList.model.size).map { it: Int -> extCellReader.valueAt(jList as JList<Any?>, it) }
        elements.any { it.toString() == containingItem } && jList.isShowing
      }
    }
    val jListFixture = JListFixture(robot(), myJList)
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
    ExtendedButtonFixture(robot(), jButton)
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
      return ComponentWithBrowseButtonFixture(component, robot())
    }
  }
  throw unableToFindComponent("ComponentWithBrowseButton with labelFor=$boundedLabelText")
}

fun <S, C : Component> ComponentFixture<S, C>.treeTable(timeout: Long = defaultTimeout): TreeTableFixture {
  if (target() is Container) {
    val table = GuiTestUtil.waitUntilFound(robot(), target() as Container,
                                           GuiTestUtilKt.typeMatcher(TreeTable::class.java) { true },
                                           timeout.toFestTimeout()
    )
    return TreeTableFixture(robot(), table)
  }
  else throw UnsupportedOperationException(
    "Sorry, unable to find inspections tree with ${target()} as a Container")
}

fun <S, C : Component> ComponentFixture<S, C>.spinner(boundedLabelText: String, timeout: Long = defaultTimeout): JSpinnerFixture {
  if (target() is Container) {
    val boundedLabel = waitUntilFound(target() as Container, JLabel::class.java, timeout) { it.text == boundedLabelText }
    val component = boundedLabel.labelFor
    if (component is JSpinner)
      return JSpinnerFixture(robot(), component)
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
    val comboBox = GuiTestUtilKt.findBoundedComponentByText(robot(), target() as Container, labelText, JComboBox::class.java)
    val comboboxFixture = ComboBoxFixture(robot(), comboBox)
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
    CheckBoxFixture(robot(), jCheckBox)
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
      ActionLinkFixture.findActionLinkByName(name, robot(), target() as Container, timeout.toFestTimeout())
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
      ActionButtonFixture.findByText(actionName, robot(), target() as Container, timeout.toFestTimeout())
    }
    catch (componentLookupException: ComponentLookupException) {
      ActionButtonFixture.findByActionId(actionName, robot(), target() as Container, timeout.toFestTimeout())
    }
  }
  else throw unableToFindComponent("""ActionButton by action name "$actionName"""")


/**
 * Finds a InplaceButton component in hierarchy of context component by icon and returns InplaceButtonFixture.
 *
 * @icon of InplaceButton component.
 * @timeout in seconds to find InplaceButton component. It is better to use static cached icons from (@see com.intellij.openapi.util.IconLoader.AllIcons)
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <S, C : Component> ComponentFixture<S, C>.inplaceButton(icon: Icon, timeout: Long = defaultTimeout): InplaceButtonFixture {
  val target = target()
  return if (target is Container) {
    InplaceButtonFixture.findInplaceButtonFixture(target, robot(), icon, timeout)
  }
  else throw unableToFindComponent("""InplaceButton by icon "$icon"""")
}

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
    ActionButtonFixture.findByActionClassName(actionClassName, robot(), target() as Container, timeout.toFestTimeout())
  }
  else throw unableToFindComponent("""ActionButton by action class name "$actionClassName"""")

/**
 * Finds a JRadioButton component in hierarchy of context component by label text and returns JRadioButtonFixture.
 *
 * @timeout in seconds to find JRadioButton component
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <S, C : Component> ComponentFixture<S, C>.radioButton(textLabel: String, timeout: Long = defaultTimeout): RadioButtonFixture =
  if (target() is Container) GuiTestUtil.findRadioButton(target() as Container, textLabel, timeout.toFestTimeout())
  else throw unableToFindComponent("""RadioButton by label "$textLabel"""")

/**
 * Finds a JTextComponent component (JTextField) in hierarchy of context component by text of label and returns JTextComponentFixture.
 *
 * @textLabel could be a null if label is absent
 * @timeout in seconds to find JTextComponent component
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <S, C : Component> ComponentFixture<S, C>.textfield(textLabel: String?, timeout: Long = defaultTimeout): JTextComponentFixture {
  val target = target()
  if (target is Container) {
    return GuiTestUtil.textfield(textLabel, target, timeout)
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
  if (target() is Container) GuiTestUtil.jTreePath(target() as Container, timeout, *pathStrings)
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
    val extendedTreeFixture = GuiTestUtil.jTreePath(target() as Container, timeout, *pathStrings)
    if (extendedTreeFixture.tree !is CheckboxTree) throw ComponentLookupException("Found JTree but not a CheckboxTree")
    CheckboxTreeFixture(robot(), extendedTreeFixture.tree)
  }
  else throw unableToFindComponent("""CheckboxTree "${if (pathStrings.isNotEmpty()) "by path $pathStrings" else ""}"""")

/**
 * Finds a JTable component in hierarchy of context component by a cellText and returns JTableFixture.
 *
 * @timeout in seconds to find JTable component
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <S, C : Component> ComponentFixture<S, C>.table(cellText: String, timeout: Long = defaultTimeout): ExtendedTableFixture =
  if (target() is Container) {
    var tableFixture: ExtendedTableFixture? = null
    waitUntilFound(target() as Container, JTable::class.java, timeout) {
      tableFixture = com.intellij.testGuiFramework.fixtures.extended.ExtendedTableFixture(robot(), it)
      try {
        tableFixture?.cell(cellText)
        tableFixture != null
      }
      catch (e: org.fest.swing.exception.ActionFailedException) {
        false
      }
    }
    tableFixture ?: throw unableToFindComponent("""JTable with cell text "$cellText"""")
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
    JBListPopupFixture.clickPopupMenuItem(itemName, false, target() as Container, robot(), timeout.toFestTimeout())
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
    val myLinkLabel = GuiTestUtil.waitUntilFound(
      robot(), target() as Container,
      GuiTestUtilKt.typeMatcher(LinkLabel::class.java) { it.isShowing && (it.text == linkName) },
      timeout.toFestTimeout())
    ComponentFixture(ComponentFixture::class.java, robot(), myLinkLabel)
  }
  else throw unableToFindComponent("LinkLabel")


fun <S, C : Component> ComponentFixture<S, C>.hyperlinkLabel(labelText: String, timeout: Long = defaultTimeout): HyperlinkLabelFixture =
  if (target() is Container) {
    val hyperlinkLabel = GuiTestUtil.waitUntilFound(robot(), target() as Container,
                                                    GuiTestUtilKt.typeMatcher(HyperlinkLabel::class.java) {
                                                      it.isShowing && (it.text == labelText)
                                                    }, timeout.toFestTimeout())
    HyperlinkLabelFixture(robot(), hyperlinkLabel)
  }
  else throw unableToFindComponent("""HyperlinkLabel by label text: "$labelText"""")

/**
 * Finds a table of plugins component in hierarchy of context component by a link name and returns fixture for it.
 *
 * @timeout in seconds to find table of plugins component
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <S, C : Component> ComponentFixture<S, C>.pluginTable(timeout: Long = defaultTimeout) =
  if (target() is Container) PluginTableFixture.find(robot(), target() as Container, timeout.toFestTimeout())
  else throw unableToFindComponent("PluginTable")

/**
 * Finds a Message component in hierarchy of context component by a title MessageFixture.
 *
 * @timeout in seconds to find component for Message
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <S, C : Component> ComponentFixture<S, C>.message(title: String, timeout: Long = defaultTimeout) =
  if (target() is Container) MessagesFixture.findByTitle(robot(), target() as Container, title, timeout.toFestTimeout())
  else throw unableToFindComponent("Message")


/**
 * Finds a Message component in hierarchy of context component by a title MessageFixture.
 *
 * @timeout in seconds to find component for Message
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <S, C : Component> ComponentFixture<S, C>.message(title: String, timeout: Long = defaultTimeout, func: MessagesFixture.() -> Unit) {
  if (target() is Container) func(MessagesFixture.findByTitle(robot(), target() as Container, title, timeout.toFestTimeout()))
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
    val jbLabel = GuiTestUtil.waitUntilFound(
      robot(), target() as Container,
      GuiTestUtilKt.typeMatcher(JBLabel::class.java) { it.isShowing && (it.text == labelName || labelName in it.text) },
      timeout.toFestTimeout())
    JLabelFixture(robot(), jbLabel)
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
  val asyncProcessIcon = GuiTestUtil.waitUntilFound(
    robot(),
    target() as Container,
    GuiTestUtilKt.typeMatcher(AsyncProcessIcon::class.java) { it.isShowing && it.isVisible },
    timeout.toFestTimeout())
  return AsyncProcessIconFixture(robot(), asyncProcessIcon)
}

internal fun Long.toFestTimeout(): Timeout = if (this == 0L) Timeout.timeout(50, TimeUnit.MILLISECONDS)
else Timeout.timeout(this, TimeUnit.SECONDS)

fun <ComponentType : Component> waitUntilFound(container: Container?,
                                               componentClass: Class<ComponentType>,
                                               timeout: Long,
                                               matcher: (ComponentType) -> Boolean): ComponentType {
  return GuiTestUtil.waitUntilFound(GuiRobotHolder.robot, container, GuiTestUtilKt.typeMatcher(componentClass) { matcher(it) }, timeout.toFestTimeout())
}
