// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.testGuiFramework.cellReader.ExtendedJComboboxCellReader
import com.intellij.testGuiFramework.cellReader.ExtendedJListCellReader
import com.intellij.testGuiFramework.fixtures.*
import com.intellij.testGuiFramework.fixtures.extended.ExtendedButtonFixture
import com.intellij.testGuiFramework.fixtures.extended.ExtendedJTreePathFixture
import com.intellij.testGuiFramework.fixtures.extended.ExtendedTableFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.Timeouts.defaultTimeout
import com.intellij.testGuiFramework.framework.toPrintable
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.typeMatcher
import com.intellij.testGuiFramework.util.FinderPredicate
import com.intellij.testGuiFramework.util.Predicate
import com.intellij.testGuiFramework.util.step
import com.intellij.ui.CheckboxTree
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.labels.ActionLink
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.AsyncProcessIcon
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.exception.ActionFailedException
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.*
import org.fest.swing.timing.Timeout
import org.junit.Assert
import java.awt.Component
import java.awt.Container
import javax.swing.*


//*********FIXTURES METHODS WITHOUT ROBOT and TARGET; KOTLIN ONLY
/**
 * Finds a JList component in hierarchy of context component with a containingItem and returns JListFixture.
 *
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.jList(containingItem: String? = null, timeout: Timeout = defaultTimeout): JListFixture {
  return step("search '$containingItem' in list") {
    val extCellReader = ExtendedJListCellReader()
    val myJList: JList<*> = findComponentWithTimeout(timeout) { jList: JList<*> ->
      if (containingItem == null) true //if were searching for any jList()
      else {
        val elements = (0 until jList.model.size).map { it: Int -> extCellReader.valueAt(jList, it) }
        elements.any { it.toString() == containingItem } && jList.isShowing
      }
    }
    val jListFixture = JListFixture(robot(), myJList)
    jListFixture.replaceCellReader(extCellReader)
    return@step jListFixture
  }
}


/**
 * Finds a JButton component in hierarchy of context component with a name and returns ExtendedButtonFixture.
 *
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.button(name: String, timeout: Timeout = defaultTimeout): ExtendedButtonFixture {
  return step("search '$name' button") {
    val jButton: JButton = findComponentWithTimeout(timeout) { it.isShowing && it.isVisible && it.text == name }
    return@step ExtendedButtonFixture(robot(), jButton)
  }
}

/**
 * Finds a list of JButton component in hierarchy of context component with a name and returns ExtendedButtonFixture.
 * There can be cases when there are several the same named components and it's OK
 *
 * @throws ComponentLookupException if no component has not been found or timeout exceeded
 * @return list of JButton components sorted by locationOnScreen (left to right, top to down)
 */
fun <C : Container> ContainerFixture<C>.buttons(name: String, timeout: Timeout = defaultTimeout): List<ExtendedButtonFixture> {
  return step("search buttons with title '$name") {
    val jButtons = waitUntilFoundList(target() as Container, JButton::class.java, timeout) {
      it.isShowing && it.isVisible && it.text == name
    }
    return@step jButtons
      .map { ExtendedButtonFixture(GuiRobotHolder.robot, it) }
      .sortedBy { it.target().locationOnScreen.x }
      .sortedBy { it.target().locationOnScreen.y }
  }
}

fun <C : Container> ContainerFixture<C>.componentWithBrowseButton(boundedLabelText: String,
                                                                  timeout: Timeout = defaultTimeout): ComponentWithBrowseButtonFixture {
  val boundedLabel: JLabel = findComponentWithTimeout(timeout) { it.text == boundedLabelText && it.isShowing }
  val component = boundedLabel.labelFor
  if (component is ComponentWithBrowseButton<*>) {
    return ComponentWithBrowseButtonFixture(component, robot())
  }
  else throw unableToFindComponent("ComponentWithBrowseButton", timeout)
}

inline fun <reified V: JComponent> ContainerFixture<*>.containsChildComponent(noinline predicate: (V) -> Boolean) =
  robot().finder().findAll(target(), GuiTestUtilKt.typeMatcher(V::class.java, predicate)).size == 1

fun <C : Container> ContainerFixture<C>.treeTable(timeout: Timeout = defaultTimeout): TreeTableFixture {
  val table: TreeTable = findComponentWithTimeout(timeout)
  return TreeTableFixture(robot(), table)
}

fun <C : Container> ContainerFixture<C>.spinner(boundedLabelText: String, timeout: Timeout = defaultTimeout): JSpinnerFixture {
  val boundedLabel: JLabel = findComponentWithTimeout(timeout) { it.text == boundedLabelText }
  val component = boundedLabel.labelFor
  if (component is JSpinner)
    return JSpinnerFixture(robot(), component)
  else throw unableToFindComponent("JSpinner", timeout)
}

fun <C : Container> ContainerFixture<C>?.unableToFindComponent(componentName: String, timeout: Timeout): Throwable {
  if (this == null) throw ComponentLookupException("Unable to find $componentName component without parent container (null) in $timeout")
  else throw ComponentLookupException(
    "Unable to find $componentName component without parent container ${this.target()::javaClass.name} in $timeout")
}

/**
 * Finds a JComboBox component in hierarchy of context component by text of label and returns ComboBoxFixture.
 *
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.combobox(labelText: String, timeout: Timeout = defaultTimeout): ComboBoxFixture {
  //todo: cut all waits in fixtures
  return step("search combobox with label '$labelText'") {
    val comboBox = GuiTestUtilKt.findBoundedComponentByText(robot(), target() as Container, labelText, JComboBox::class.java, timeout)
    val comboboxFixture = ComboBoxFixture(robot(), comboBox)
    comboboxFixture.replaceCellReader(ExtendedJComboboxCellReader())
    return@step comboboxFixture
  }
}


/**
 * Finds a JCheckBox component in hierarchy of context component by text of label and returns CheckBoxFixture.
 *
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.checkbox(labelText: String, timeout: Timeout = defaultTimeout): CheckBoxFixture {
  return step("search checkbox with label '$labelText'") {
    val jCheckBox: JCheckBox = findComponentWithTimeout(timeout) { it.isShowing && it.isVisible && it.text == labelText }
    return@step CheckBoxFixture(robot(), jCheckBox)
  }
}

fun <C : Container> ContainerFixture<C>.checkboxContainingText(labelText: String,
                                                               ignoreCase: Boolean = false,
                                                               timeout: Timeout = defaultTimeout): CheckBoxFixture {
  val jCheckBox: JCheckBox = findComponentWithTimeout(timeout) { it.isShowing && it.isVisible && it.text.contains(labelText, ignoreCase) }
  return CheckBoxFixture(robot(), jCheckBox)
}

/**
 * Finds a ActionLink component in hierarchy of context component by name and returns ActionLinkFixture.
 *
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.actionLink(name: String, timeout: Timeout = defaultTimeout): ActionLinkFixture {
  return step("search action link '$name'") {
    val actionLink: ActionLink = findComponentWithTimeout(timeout) { it.isVisible && it.isShowing && it.text == name }
    return@step ActionLinkFixture(robot(), actionLink)
  }
}

/**
 * Finds a ActionButton component in hierarchy of context component by action name and returns ActionButtonFixture.
 *
 * @actionName text or action id of an action button (@see com.intellij.openapi.actionSystem.ActionManager#getId())
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.actionButton(actionName: String, timeout: Timeout = defaultTimeout): ActionButtonFixture {
  return step("search action button '$actionName'") {
    val actionButton: ActionButton = try {
      findComponentWithTimeout(timeout, ActionButtonFixture.textMatcher(actionName))
    }
    catch (componentLookupException: ComponentLookupException) {
      findComponentWithTimeout(timeout, ActionButtonFixture.actionIdMatcher(actionName))
    }
    return@step ActionButtonFixture(robot(), actionButton)
  }
}

fun <C : Container> ContainerFixture<C>.actionButton(actionName: String, filter: (ActionButton) -> Boolean, timeout: Timeout = defaultTimeout): ActionButtonFixture {
  val actionButton: ActionButton = try {
    findComponentWithTimeout(timeout) { ActionButtonFixture.textMatcher(actionName).invoke(it).and(filter.invoke(it)) }
  }
  catch (componentLookupException: ComponentLookupException) {
    findComponentWithTimeout(timeout) { ActionButtonFixture.actionIdMatcher(actionName).invoke(it).and(filter.invoke(it)) }
  }
  return ActionButtonFixture(robot(), actionButton)
}

/**
 * Finds a InplaceButton component in hierarchy of context component by icon and returns InplaceButtonFixture.
 *
 * @icon of InplaceButton component.
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.inplaceButton(icon: Icon, timeout: Timeout = defaultTimeout): InplaceButtonFixture {
  val target = target()
  return InplaceButtonFixture.findInplaceButtonFixture(target, robot(), icon, timeout)
}

/**
 * Finds a ActionButton component in hierarchy of context component by action class name and returns ActionButtonFixture.
 *
 * @actionClassName qualified name of class for action
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.actionButtonByClass(actionClassName: String,
                                                            timeout: Timeout = defaultTimeout): ActionButtonFixture {
  val actionButton: ActionButton = findComponentWithTimeout(timeout, ActionButtonFixture.actionClassNameMatcher(actionClassName))
  return ActionButtonFixture(robot(), actionButton)

}


fun <C : Container> ContainerFixture<C>.tab(textLabel: String, timeout: Timeout = defaultTimeout): JBTabbedPaneFixture {
  val jbTabbedPane: JBTabbedPane = findComponentWithTimeout(timeout) {
    it.indexOfTab(textLabel) != -1
  }
  return JBTabbedPaneFixture(textLabel, jbTabbedPane, robot())
}

/**
 * Finds a JRadioButton component in hierarchy of context component by label text and returns JRadioButtonFixture.
 *
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.radioButton(textLabel: String, timeout: Timeout = defaultTimeout): RadioButtonFixture =
  GuiTestUtil.findRadioButton(target() as Container, textLabel, timeout)

/**
 * Finds a JTextComponent component (JTextField) in hierarchy of context component by text of label and returns JTextComponentFixture.
 *
 * @textLabel could be a null if label is absent
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.textfield(textLabel: String?, timeout: Timeout = defaultTimeout): JTextComponentFixture {
  return step("search textfield with label '$textLabel'") {
    return@step GuiTestUtil.textfield(textLabel, target(), timeout)
  }
}

/**
 * Finds a JTree component in hierarchy of context component by a path and returns ExtendedTreeFixture.
 *
 * @pathStrings comma separated array of Strings, representing path items: jTree("myProject", "src", "Main.java")
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.jTree(vararg pathStrings: String,
                                              timeout: Timeout = defaultTimeout,
                                              predicate: FinderPredicate = Predicate.equality): ExtendedJTreePathFixture =
  ExtendedJTreePathFixture(GuiTestUtil.jTreeComponent(
    container = target() as Container,
    timeout = timeout,
    pathStrings = *pathStrings,
    predicate = predicate
  ), pathStrings.toList(), predicate)

/**
 * Finds a CheckboxTree component in hierarchy of context component by a path and returns CheckboxTreeFixture.
 *
 * @pathStrings comma separated array of Strings, representing path items: checkboxTree("JBoss", "JBoss Drools")
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.checkboxTree(
  vararg pathStrings: String,
  timeout: Timeout = defaultTimeout,
  predicate: FinderPredicate = Predicate.equality
): CheckboxTreeFixture {
  val tree = GuiTestUtil.jTreeComponent(
    container = target() as Container,
    timeout = timeout,
    predicate = predicate,
    pathStrings = *pathStrings
  ) as? CheckboxTree ?: throw ComponentLookupException("Found JTree but not a CheckboxTree")
  return CheckboxTreeFixture(tree, pathStrings.toList(), predicate, robot())
}

/**
 * Finds a JTable component in hierarchy of context component by a cellText and returns JTableFixture.
 *
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.table(cellText: String, timeout: Timeout = defaultTimeout): ExtendedTableFixture {
  var tableFixture: ExtendedTableFixture? = null
  val jTable: JTable = findComponentWithTimeout(timeout) {
    tableFixture = ExtendedTableFixture(robot(), it)
    try {
      tableFixture?.cell(cellText)
      tableFixture != null
    }
    catch (e: ActionFailedException) {
      false
    }
  }
  return tableFixture ?: throw unableToFindComponent("""JTable with cell text "$cellText"""", timeout)
}

fun popupMenu(
  item: String,
  robot: Robot,
  root: Container? = null,
  timeout: Timeout = defaultTimeout,
  predicate: FinderPredicate = Predicate.equality
): JBListPopupFixture {
  val jbList = GuiTestUtil.waitUntilFound(
    robot,
    root,
    object : GenericTypeMatcher<JBList<*>>(JBList::class.java) {
      override fun isMatching(component: JBList<*>): Boolean {
        return JBListPopupFixture(component, item, predicate, robot).isSearchedItemPresent()
      }
    },
    timeout)
  return JBListPopupFixture(jbList, item, predicate, robot)

}

fun <C : Container> ContainerFixture<C>.popupMenu(item: String,
                                                  timeout: Timeout = defaultTimeout,
                                                  predicate: FinderPredicate = Predicate.equality): JBListPopupFixture {
  return step("search '$item' in popup menu ") {
    val root: Container? = GuiTestUtil.getRootContainer(target())
    Assert.assertNotNull(root)
    return@step popupMenu(item, robot(), root, timeout, predicate)
  }
}


/**
 * Finds a LinkLabel component in hierarchy of context component by a link name and returns fixture for it.
 *
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.linkLabel(linkName: String,
                                                  timeout: Timeout = defaultTimeout): ComponentFixture<ComponentFixture<*, *>, LinkLabel<*>> {
  val myLinkLabel = GuiTestUtil.waitUntilFound(
    robot(), target() as Container,
    GuiTestUtilKt.typeMatcher(LinkLabel::class.java) { it.isShowing && (it.text == linkName) },
    timeout)
  return ComponentFixture(ComponentFixture::class.java, robot(), myLinkLabel)
}


fun <C : Container> ContainerFixture<C>.hyperlinkLabel(labelText: String,
                                                       timeout: Timeout = defaultTimeout): HyperlinkLabelFixture {
  val hyperlinkLabel = GuiTestUtil.waitUntilFound(robot(), target() as Container,
                                                  GuiTestUtilKt.typeMatcher(HyperlinkLabel::class.java) {
                                                    it.isShowing && (it.text == labelText)
                                                  }, timeout)
  return HyperlinkLabelFixture(robot(), hyperlinkLabel)
}

/**
 * Finds a table of plugins component in hierarchy of context component by a link name and returns fixture for it.
 *
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.pluginTable(timeout: Timeout = defaultTimeout) =
  PluginTableFixture.find(robot(), target() as Container, timeout)

/**
 * Finds a Message component in hierarchy of context component by a title MessageFixture.
 *
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.message(title: String, timeout: Timeout = defaultTimeout): MessagesFixture<*> =
  MessagesFixture.findByTitle(robot(), target() as Container, title, timeout)


fun <C : Container> ContainerFixture<C>.message(title: String,
                                                timeout: Timeout = defaultTimeout,
                                                func: MessagesFixture<*>.() -> Unit) {
  func(MessagesFixture.findByTitle(robot(), target() as Container, title, timeout))
}

/**
 * Finds a JBLabel component in hierarchy of context component by a label name and returns fixture for it.
 *
 * @throws ComponentLookupException if component has not been found or timeout exceeded
 */
fun <C : Container> ContainerFixture<C>.label(labelName: String, timeout: Timeout = defaultTimeout): JLabelFixture {
  val jbLabel = GuiTestUtil.waitUntilFound(
    robot(), target() as Container,
    GuiTestUtilKt.typeMatcher(JBLabel::class.java) { it.isShowing && (it.text == labelName || labelName in it.text) },
    timeout)
  return JLabelFixture(robot(), jbLabel)
}

/**
 * Find an AsyncProcessIcon component in a current context (gets by receiver) and returns a fixture for it.
 * Indexing processIcon is excluded from this search
 */
fun <C : Container> ContainerFixture<C>.asyncProcessIcon(timeout: Timeout = defaultTimeout): AsyncProcessIconFixture {
  val indexingProcessIconTooltipText = ActionsBundle.message("action.ShowProcessWindow.double.click")
  return asyncProcessIconByTooltip(indexingProcessIconTooltipText, Predicate.notEquality, timeout)
}

/**
 * Find an AsyncProcessIcon component corresponding to background tasks
 * @return fixture of AsyncProcessIcon
 * @throws WaitTimedOutError if no icon is found
 */
fun <C : Container> ContainerFixture<C>.indexingProcessIcon(timeout: Timeout = defaultTimeout): AsyncProcessIconFixture {
  val indexingProcessIconTooltipText = ActionsBundle.message("action.ShowProcessWindow.double.click")
  return asyncProcessIconByTooltip(indexingProcessIconTooltipText, Predicate.equality, timeout)
}

/**
 * Find an AsyncProcessIcon component corresponding to background tasks
 * @return if found - fixture of AsyncProcessIcon, or null if not found
 */
fun <C : Container> ContainerFixture<C>.indexingProcessIconNullable(timeout: Timeout = defaultTimeout): AsyncProcessIconFixture? {
  val indexingProcessIconTooltipText = ActionsBundle.message("action.ShowProcessWindow.double.click")
  return try {
    asyncProcessIconByTooltip(indexingProcessIconTooltipText, Predicate.equality, timeout)
  }
  catch (ignored: WaitTimedOutError) {
    // asyncIcon not found and it's OK, so no background process is going
    null
  }
}

/**
 * Find an AsyncProcessIcon component by tooltip and predicate
 * @param expectedTooltip
 */
fun <C : Container> ContainerFixture<C>.asyncProcessIconByTooltip(expectedTooltip: String, predicate: FinderPredicate, timeout: Timeout = defaultTimeout): AsyncProcessIconFixture {
  val asyncProcessIcon = GuiTestUtil.waitUntilFound(
    robot(),
    target(),
    GuiTestUtilKt.typeMatcher(AsyncProcessIcon::class.java) {
      it.isShowing &&
      it.isVisible &&
      predicate(it.toolTipText ?: "", expectedTooltip)
    },
    timeout)
  return AsyncProcessIconFixture(robot(), asyncProcessIcon)
}

fun <ComponentType : Component> waitUntilFound(container: Container?,
                                               componentClass: Class<ComponentType>,
                                               timeout: Timeout,
                                               matcher: (ComponentType) -> Boolean): ComponentType {
  return GuiTestUtil.waitUntilFound(GuiRobotHolder.robot, container, GuiTestUtilKt.typeMatcher(componentClass) { matcher(it) },
                                    timeout)
}

fun <ComponentType : Component> waitUntilFoundList(container: Container?,
                                                   componentClass: Class<ComponentType>,
                                                   timeout: Timeout,
                                                   matcher: (ComponentType) -> Boolean): List<ComponentType> {
  return GuiTestUtil.waitUntilFoundList(container, timeout, GuiTestUtilKt.typeMatcher(componentClass) { matcher(it) })
}

/**
 * function to find component of returning type inside a container (gets from receiver).
 *
 * @throws ComponentLookupException if desired component haven't been found under the container (gets from receiver) in specified timeout
 */
inline fun <reified ComponentType : Component, ContainerComponentType : Container> ContainerFixture<ContainerComponentType>?.findComponentWithTimeout(
  timeout: Timeout = defaultTimeout,
  crossinline finderFunction: (ComponentType) -> Boolean = { true }): ComponentType {
  try {
    return GuiTestUtil.waitUntilFound(GuiRobotHolder.robot, this?.target() as Container?,
                                      GuiTestUtilKt.typeMatcher(ComponentType::class.java) { finderFunction(it) },
                                      timeout)
  }
  catch (e: WaitTimedOutError) {
    throw ComponentLookupException(
      "Unable to find ${ComponentType::class.java.name} ${if (this?.target() != null) "in container ${this.target()}" else ""} in ${timeout.toPrintable()}")
  }
}

fun <ComponentType : Component> findComponentWithTimeout(container: Container?,
                                                         componentClass: Class<ComponentType>,
                                                         timeout: Timeout = defaultTimeout,
                                                         finderFunction: (ComponentType) -> Boolean = { true }): ComponentType {
  try {
    return GuiTestUtil.waitUntilFound(GuiRobotHolder.robot, container, GuiTestUtilKt.typeMatcher(componentClass) { finderFunction(it) },
                                      timeout)
  }
  catch (e: WaitTimedOutError) {
    throw ComponentLookupException(
      "Unable to find ${componentClass.simpleName} ${if (container != null) "in container $container" else ""} in ${timeout.toPrintable()}")
  }
}

fun <ComponentType : Component> Robot.findComponent(container: Container?,
                                                    componentClass: Class<ComponentType>,
                                                    finderFunction: (ComponentType) -> Boolean = { true }): ComponentType {
  return if (container == null)
    this.finder().find(typeMatcher(componentClass, finderFunction))
  else
    this.finder().find(container, typeMatcher(componentClass, finderFunction))
}
