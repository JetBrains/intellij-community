/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.generators

import com.intellij.framework.PresentableVersion
import com.intellij.ide.plugins.PluginTable
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Ref
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame
import com.intellij.platform.ProjectTemplate
import com.intellij.testGuiFramework.fixtures.MessageDialogFixture
import com.intellij.testGuiFramework.fixtures.MessagesFixture
import com.intellij.testGuiFramework.fixtures.SettingsTreeFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.generators.Utils.clicks
import com.intellij.testGuiFramework.generators.Utils.convertSimpleTreeItemToPath
import com.intellij.testGuiFramework.generators.Utils.getBoundedLabelForComboBox
import com.intellij.testGuiFramework.generators.Utils.getCellText
import com.intellij.testGuiFramework.generators.Utils.getJTreePath
import com.intellij.testGuiFramework.generators.Utils.getLabel
import com.intellij.ui.CheckboxTree
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.labels.ActionLink
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.messages.SheetController
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.util.ui.tree.TreeUtil
import org.fest.reflect.core.Reflection.field
import org.fest.swing.core.BasicRobot
import org.fest.swing.core.ComponentMatcher
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.exception.ComponentLookupException
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.event.MouseEvent
import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.util.*
import java.util.jar.JarFile
import javax.swing.*
import javax.swing.plaf.basic.BasicArrowButton
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/**
 * @author Sergey Karashevich
 */

//**********COMPONENT GENERATORS**********

private val leftButton = MouseEvent.BUTTON1
private val rightButton = MouseEvent.BUTTON3

private fun MouseEvent.isLeftButton() = (this.button == leftButton)
private fun MouseEvent.isRightButton() = (this.button == rightButton)

class JButtonGenerator : ComponentCodeGenerator<JButton> {
  override fun accept(cmp: Component) = cmp is JButton
  override fun generate(cmp: JButton, me: MouseEvent, cp: Point) = "button(\"${cmp.text}\").click()"
}

class ActionButtonGenerator : ComponentCodeGenerator<ActionButton> {
  override fun accept(cmp: Component) = cmp is ActionButton
  override fun generate(cmp: ActionButton, me: MouseEvent, cp: Point) = "actionButton(\"${cmp.action.templatePresentation.text}\").click()"
}

class ActionLinkGenerator : ComponentCodeGenerator<ActionLink> {
  override fun accept(cmp: Component) = cmp is ActionLink
  override fun generate(cmp: ActionLink, me: MouseEvent, cp: Point) = "actionLink(\"${cmp.text}\").click()"
}

class JTextFieldGenerator : ComponentCodeGenerator<JTextField> {
  override fun accept(cmp: Component) = cmp is JTextField
  override fun generate(cmp: JTextField, me: MouseEvent, cp: Point) = "textfield(\"${getLabel(cmp)?.text.orEmpty()}\").${clicks(
    me)}"
}

class JBListGenerator : ComponentCodeGenerator<JBList<*>> {
  override fun priority() = 1
  override fun accept(cmp: Component) = cmp is JBList<*>
  private fun JBList<*>.isPopupList() = this.javaClass.name.toLowerCase().contains("listpopup")
  private fun JBList<*>.isFrameworksTree() = this.javaClass.name.toLowerCase().contains("AddSupportForFrameworksPanel".toLowerCase())
  override fun generate(cmp: JBList<*>, me: MouseEvent, cp: Point): String {
    val cellText = getCellText(cmp, cp).orEmpty()
    if (cmp.isPopupList()) return "popupClick(\"$cellText\")"
    if (me.button == MouseEvent.BUTTON2) return "jList(\"$cellText\").item(\"$cellText\").rightClick()"
    return "jList(\"$cellText\").clickItem(\"$cellText\")"
  }
}

class BasicComboPopupGenerator : ComponentCodeGenerator<JList<*>> {
  override fun accept(cmp: Component) = cmp is JList<*> && cmp.javaClass.name.contains("BasicComboPopup")
  override fun generate(cmp: JList<*>, me: MouseEvent, cp: Point): String {
    val cellText = getCellText(cmp, cp).orEmpty()
    return ".selectItem(\"$cellText\")" // check that combobox is open
  }
}

class CheckboxTreeGenerator : ComponentCodeGenerator<CheckboxTree> {
  override fun accept(cmp: Component) = cmp is CheckboxTree
  override fun generate(cmp: CheckboxTree, me: MouseEvent, cp: Point) = "selectFramework(\"${cmp.getClosestPathForLocation(cp.x,
                                                                                                                           cp.y).lastPathComponent}\")"
}

class SimpleTreeGenerator : ComponentCodeGenerator<SimpleTree> {
  override fun accept(cmp: Component) = cmp is SimpleTree
  private fun SimpleTree.getPath(cp: Point) = convertSimpleTreeItemToPath(this, this.getDeepestRendererComponentAt(cp.x, cp.y).toString())
  override fun generate(cmp: SimpleTree, me: MouseEvent, cp: Point): String {
    val path = cmp.getPath(cp)
    if (me.isRightButton()) return "jTree(\"$path\").rightClickPath(\"$path\")"
    return "jTree(\"$path\").selectPath(\"$path\")"
  }
}

class JBCheckBoxGenerator : ComponentCodeGenerator<JBCheckBox> {
  override fun priority() = 1
  override fun accept(cmp: Component) = cmp is JBCheckBox
  override fun generate(cmp: JBCheckBox, me: MouseEvent, cp: Point) = "checkbox(\"${cmp.text}\").click()"
}

class JCheckBoxGenerator : ComponentCodeGenerator<JCheckBox> {
  override fun accept(cmp: Component) = cmp is JCheckBox
  override fun generate(cmp: JCheckBox, me: MouseEvent, cp: Point) = "checkbox(\"${cmp.text}\").click()"
}

class JComboBoxGenerator : ComponentCodeGenerator<JComboBox<*>> {
  override fun accept(cmp: Component) = cmp is JComboBox<*>
  override fun generate(cmp: JComboBox<*>, me: MouseEvent, cp: Point) = "combobox(\"${getBoundedLabelForComboBox(cmp).text}\")"
}

class BasicArrowButtonDelegatedGenerator : ComponentCodeGenerator<BasicArrowButton> {
  override fun priority() = 1 //make sense if we challenge with simple jbutton
  override fun accept(cmp: Component) = (cmp is BasicArrowButton) && (cmp.parent is JComboBox<*>)
  override fun generate(cmp: BasicArrowButton, me: MouseEvent, cp: Point) = JComboBoxGenerator().generate(cmp.parent as JComboBox<*>, me,
                                                                                                          cp)
}

class JRadioButtonGenerator : ComponentCodeGenerator<JRadioButton> {
  override fun accept(cmp: Component) = cmp is JRadioButton
  override fun generate(cmp: JRadioButton, me: MouseEvent, cp: Point) = "radioButton(\"${cmp.text}\").select()"
}

class LinkLabelGenerator : ComponentCodeGenerator<LinkLabel<*>> {
  override fun accept(cmp: Component) = cmp is LinkLabel<*>
  override fun generate(cmp: LinkLabel<*>, me: MouseEvent, cp: Point) = "radioButton(\"${cmp.text}\").select()"
}

class JTreeGenerator : ComponentCodeGenerator<JTree> {
  override fun accept(cmp: Component) = cmp is JTree
  private fun JTree.getPath(cp: Point) = this.getClosestPathForLocation(cp.x, cp.y)
  override fun generate(cmp: JTree, me: MouseEvent, cp: Point): String {
    val path = getJTreePath(cmp, cmp.getPath(cp))
    if (me.isRightButton()) return "jTree(\"$path\").rightClickPath(\"$path\")"
    return "jTree(\"$path\").clickPath(\"$path\")"
  }
}

class PluginTableGenerator : ComponentCodeGenerator<PluginTable> {
  override fun accept(cmp: Component) = cmp is PluginTable

  override fun generate(cmp: PluginTable, me: MouseEvent, cp: Point): String {
    val row = cmp.rowAtPoint(cp)
    val ideaPluginDescriptor = cmp.getObjectAt(row)
    return "pluginTable().selectPlugin(\"${ideaPluginDescriptor.name}\")"
  }
}

class EditorComponentGenerator : ComponentCodeGenerator<EditorComponentImpl> {
  override fun accept(cmp: Component) = cmp is EditorComponentImpl

  override fun generate(cmp: EditorComponentImpl, me: MouseEvent, cp: Point): String {
    val editor = cmp.editor
    val logicalPos = editor.xyToLogicalPosition(cp)
    val offset = editor.logicalPositionToOffset(logicalPos)
    when (me.button) {
      leftButton -> return "moveTo($offset)"
      rightButton -> return "rightClick($offset)"
      else -> return "//not implemented editor action"
    }
  }
}

class ActionMenuItemGenerator : ComponentCodeGenerator<ActionMenuItem> {

  override fun accept(cmp: Component) = cmp is ActionMenuItem

  override fun generate(cmp: ActionMenuItem, me: MouseEvent, cp: Point) =
    "popup(${buildPath(activatedActionMenuItem = cmp).joinToString(separator = ", ") { str -> "\"$str\"" }})"


  //for buildnig a path of actionMenus and actionMenuItem we need to scan all JBPopup and find a consequence of actions from a tail. Each discovered JBPopupMenu added to hashSet to avoid double sacnning and multiple component finding results
  private fun buildPath(activatedActionMenuItem: ActionMenuItem): List<String> {
    val jbPopupMenuSet = HashSet<Int>()
    jbPopupMenuSet.add(activatedActionMenuItem.parent.hashCode())

    val path = ArrayList<String>()
    var actionItemName = activatedActionMenuItem.text
    path.add(actionItemName)
    var window = activatedActionMenuItem.getNextPopupSHeavyWeightWindow()
    while (window?.getNextPopupSHeavyWeightWindow() != null) {
      window = window!!.getNextPopupSHeavyWeightWindow()
      actionItemName = window!!.findJBPopupMenu(jbPopupMenuSet).findParentActionMenu(jbPopupMenuSet)
      path.add(0, actionItemName)
    }
    return path
  }


  private fun Component.getNextPopupSHeavyWeightWindow(): JWindow? {
    if (this.parent == null) return null
    var cmp = this.parent
    while (cmp != null && !cmp.javaClass.name.endsWith("Popup\$HeavyWeightWindow")) cmp = cmp.parent
    if (cmp == null) return null
    return cmp as JWindow
  }

  private fun JWindow.findJBPopupMenu(jbPopupHashSet: MutableSet<Int>): JBPopupMenu {
    val robot = BasicRobot.robotWithCurrentAwtHierarchy()
    val resultJBPopupMenu = robot.finder().find(this, ComponentMatcher { component ->
      (component is JBPopupMenu)
      && component.isShowing
      && component.isVisible
      && !jbPopupHashSet.contains(component.hashCode())
    }) as JBPopupMenu
    jbPopupHashSet.add(resultJBPopupMenu.hashCode())
    robot.cleanUpWithoutDisposingWindows()
    return resultJBPopupMenu
  }

  private fun JBPopupMenu.findParentActionMenu(jbPopupHashSet: MutableSet<Int>): String {
    val actionMenu: ActionMenu = this.subElements
                                   .filterIsInstance(ActionMenu::class.java)
                                   .filterNotNull()
                                   .find { actionMenu ->
                                     (actionMenu.subElements != null
                                      && actionMenu.subElements.isNotEmpty()
                                      && actionMenu.subElements
                                        .any { menuElement ->
                                          (menuElement is JBPopupMenu && jbPopupHashSet.contains(menuElement.hashCode()))
                                        })
                                   } ?: throw Exception("Unable to find a proper ActionMenu")
    return actionMenu.text
  }
}

//**********GLOBAL CONTEXT GENERATORS**********

class WelcomeFrameGenerator : GlobalContextCodeGenerator<FlatWelcomeFrame>() {
  override fun priority() = 1
  override fun accept(cmp: Component) = (cmp as JComponent).rootPane.parent is FlatWelcomeFrame
  override fun generate(cmp: FlatWelcomeFrame, me: MouseEvent, cp: Point): String {
    return "welcomeFrame {"
  }
}

class JDialogGenerator : GlobalContextCodeGenerator<JDialog>() {
  override fun accept(cmp: Component) = (cmp as JComponent).rootPane.parent is JDialog
  override fun generate(cmp: JDialog, me: MouseEvent, cp: Point) = "dialog(\"${cmp.title}\") {"
}

class IdeFrameGenerator : GlobalContextCodeGenerator<JFrame>() {
  override fun accept(cmp: Component): Boolean {
    val parent = (cmp as JComponent).rootPane.parent
    return (parent is JFrame) && parent.title != "GUI Script Editor"
  }

  override fun generate(cmp: JFrame, me: MouseEvent, cp: Point) = "ideFrame {"
}

class MacMessageGenerator : ComponentCodeGenerator<JDialog> {

  override fun priority() = 1

  override fun accept(cmp: Component): Boolean {
    if (!(Messages.canShowMacSheetPanel() && (cmp as JComponent).rootPane.parent is JDialog)) return false
    val panel = cmp.rootPane.contentPane as JPanel

    if (panel.javaClass.name.startsWith(SheetController::class.java.name) && panel.isShowing()) {
      val controller = MessagesFixture.findSheetController(panel)
      val sheetPanel = field("mySheetPanel").ofType(JPanel::class.java).`in`(controller).get()
      if (sheetPanel === panel) {
        return true
      }
    }
    return false
  }

  override fun generate(cmp: JDialog, me: MouseEvent, cp: Point): String {
    val panel = cmp.rootPane.contentPane as JPanel
    val myRobot = BasicRobot.robotWithCurrentAwtHierarchyWithoutScreenLock()
    val title = MessagesFixture.getTitle(panel, myRobot)
    myRobot.cleanUpWithoutDisposingWindows()
    return "message(\"$title\") {"
  }
}

class MessageGenerator : ComponentCodeGenerator<JDialog> {

  override fun priority() = 1

  override fun accept(cmp: Component): Boolean =
    cmp is JDialog && MessageDialogFixture.isMessageDialog(cmp, Ref<DialogWrapper>())

  override fun generate(cmp: JDialog, me: MouseEvent, cp: Point): String {
    return "message(\"${cmp.title}\") {"
  }
}

//**********LOCAL CONTEXT GENERATORS**********

class ProjectViewGenerator : LocalContextCodeGenerator<JPanel>() {

  override fun priority() = 1
  override fun acceptor(): (Component) -> Boolean = { component -> component.javaClass.name.endsWith("ProjectViewImpl\$MyPanel") }
  override fun generate(cmp: JPanel, me: MouseEvent, cp: Point) = "projectView {"

}

class ToolWindowGenerator : LocalContextCodeGenerator<Component>() {

  private fun Component.containsLocationOnScreen(locationOnScreen: Point): Boolean {
    val rectangle = this.bounds
    rectangle.location = this.locationOnScreen
    return rectangle.contains(locationOnScreen)
  }

  private fun Component.centerOnScreen(): Point {
    val rectangle = this.bounds
    rectangle.location = this.locationOnScreen
    return Point(rectangle.centerX.toInt(), rectangle.centerY.toInt())
  }

  private fun getToolWindow(pointOnScreen: Point): ToolWindowImpl? {
    if (WindowManagerImpl.getInstance().findVisibleFrame() !is IdeFrameImpl) return null
    val ideFrame = WindowManagerImpl.getInstance().findVisibleFrame() as IdeFrameImpl
    ideFrame.project ?: return null
    val toolWindowManager = ToolWindowManagerImpl.getInstance(ideFrame.project!!)
    val visibleToolWindows = toolWindowManager.toolWindowIds
      .map { toolWindowId -> toolWindowManager.getToolWindow(toolWindowId) }
      .filter { toolwindow -> toolwindow.isVisible }
    val toolwindow: ToolWindowImpl = visibleToolWindows
                                       .filterIsInstance<ToolWindowImpl>()
                                       .find { it.component.containsLocationOnScreen(pointOnScreen) } ?: return null
    return toolwindow
  }

  override fun acceptor(): (Component) -> Boolean = { component ->
    val tw = getToolWindow(component.centerOnScreen()); tw != null && component == tw.component
  }

  override fun generate(cmp: Component, me: MouseEvent, cp: Point): String {
    val toolWindow: ToolWindowImpl = getToolWindow(cmp.centerOnScreen())!!
    return "toolwindow(id = \"${toolWindow.id}\") {"
  }

}

class ToolWindowContextGenerator : LocalContextCodeGenerator<Component>() {

  override fun priority() = 1

  private fun Component.containsLocationOnScreen(locationOnScreen: Point): Boolean {
    val rectangle = this.bounds
    rectangle.location = this.locationOnScreen
    return rectangle.contains(locationOnScreen)
  }

  private fun Component.centerOnScreen(): Point {
    val rectangle = this.bounds
    rectangle.location = this.locationOnScreen
    return Point(rectangle.centerX.toInt(), rectangle.centerY.toInt())
  }

  private fun Component.contains(component: Component): Boolean {
    return this.contains(Point(component.bounds.x, component.bounds.y)) &&
           this.contains(Point(component.bounds.x + component.width, component.bounds.y + component.height))
  }

  private fun getToolWindow(pointOnScreen: Point): ToolWindowImpl? {
    if (WindowManagerImpl.getInstance().findVisibleFrame() !is IdeFrameImpl) return null
    val ideFrame = WindowManagerImpl.getInstance().findVisibleFrame() as IdeFrameImpl
    ideFrame.project ?: return null
    val toolWindowManager = ToolWindowManagerImpl.getInstance(ideFrame.project!!)
    val visibleToolWindows = toolWindowManager.toolWindowIds
      .map { toolWindowId -> toolWindowManager.getToolWindow(toolWindowId) }
      .filter { toolwindow -> toolwindow.isVisible }
    val toolwindow: ToolWindowImpl = visibleToolWindows
                                       .filterIsInstance<ToolWindowImpl>()
                                       .find { it.component.containsLocationOnScreen(pointOnScreen) } ?: return null
    return toolwindow
  }

  override fun acceptor(): (Component) -> Boolean = { component ->
    val tw = getToolWindow(component.centerOnScreen()); tw != null && tw.contentManager.selectedContent!!.component == component
  }

  override fun generate(cmp: Component, me: MouseEvent, cp: Point): String {
    val toolWindow: ToolWindowImpl = getToolWindow(cmp.centerOnScreen())!!
    val tabName = toolWindow.contentManager.selectedContent?.tabName
    return if (tabName != null) "content(tabName = \"${tabName}\") {"
    else "content {"
  }

}

class EditorGenerator : LocalContextCodeGenerator<EditorComponentImpl>() {

  override fun acceptor(): (Component) -> Boolean = { component -> component is EditorComponentImpl }
  override fun generate(cmp: EditorComponentImpl, me: MouseEvent, cp: Point) = "editor {"

}


//class JBPopupMenuGenerator: LocalContextCodeGenerator<JBPopupMenu>() {
//
//  override fun acceptor(): (Component) -> Boolean = { component -> component is JBPopupMenu}
//  override fun generate(cmp: JBPopupMenu, me: MouseEvent, cp: Point) = "popupMenu {"
//}


object Generators {
  fun getGenerators(): List<ComponentCodeGenerator<*>> {
    val generatorClassPaths = getSiblingsList().filter { path -> path.endsWith("Generator.class") }
    val classLoader = Generators.javaClass.classLoader
    return generatorClassPaths
      .map { clzPath -> classLoader.loadClass("${Generators.javaClass.`package`.name}.${File(clzPath).nameWithoutExtension}") }
      .filter { clz -> clz.interfaces.contains(ComponentCodeGenerator::class.java) }
      .map(Class<*>::newInstance)
      .filterIsInstance(ComponentCodeGenerator::class.java)
  }

  fun getGlobalContextGenerators(): List<GlobalContextCodeGenerator<*>> {
    val generatorClassPaths = getSiblingsList().filter { path -> path.endsWith("Generator.class") }
    val classLoader = Generators.javaClass.classLoader
    return generatorClassPaths
      .map { clzPath -> classLoader.loadClass("${Generators.javaClass.`package`.name}.${File(clzPath).nameWithoutExtension}") }
      .filter { clz -> clz.superclass == GlobalContextCodeGenerator::class.java }
      .map(Class<*>::newInstance)
      .filterIsInstance(GlobalContextCodeGenerator::class.java)
  }

  fun getLocalContextCodeGenerator(): List<LocalContextCodeGenerator<*>> {
    val generatorClassPaths = getSiblingsList().filter { path -> path.endsWith("Generator.class") }
    val classLoader = Generators.javaClass.classLoader
    return generatorClassPaths
      .map { clzPath -> classLoader.loadClass("${Generators.javaClass.`package`.name}.${File(clzPath).nameWithoutExtension}") }
      .filter { clz -> clz.superclass == LocalContextCodeGenerator::class.java }
      .map(Class<*>::newInstance)
      .filterIsInstance(LocalContextCodeGenerator::class.java)
  }

  fun getSiblingsList(): List<String> {
    val path = "/${Generators.javaClass.`package`.name.replace(".", "/")}"
    val url = Generators.javaClass.getResource(path)
    if (url.path.contains(".jar!")) {
      val jarFile = JarFile(Paths.get(URI(url.file.substringBefore(".jar!").plus(".jar"))).toString())
      val entries = jarFile.entries()
      val genPath = url.path.substringAfter(".jar!").removePrefix("/")
      return entries.toList().filter { entry -> entry.name.contains(genPath) }.map { entry -> entry.name }
    }
    else return File(url.toURI()).listFiles().map { file -> file.toURI().path }
  }

}

object Utils {

  fun getLabel(container: Container, jTextField: JTextField): JLabel? {
    val robot = BasicRobot.robotWithCurrentAwtHierarchyWithoutScreenLock()
    return GuiTestUtil.findBoundedLabel(container, jTextField, robot)
  }

  fun getLabel(jTextField: JTextField): JLabel? {
    val parentContainer = jTextField.rootPane.parent
    val robot = BasicRobot.robotWithCurrentAwtHierarchyWithoutScreenLock()
    return GuiTestUtil.findBoundedLabel(parentContainer, jTextField, robot)
  }

  fun clicks(me: MouseEvent): String {
    if (me.clickCount == 1) return "click()"
    if (me.clickCount == 2) return "doubleClick()"
    return ""
  }

  fun getCellText(jbList: JBList<*>, pointOnList: Point): String? {
    val index = jbList.locationToIndex(pointOnList)
    val cellBounds = jbList.getCellBounds(index, index)
    if (cellBounds.contains(pointOnList)) {
      val elementAt = jbList.model.getElementAt(index)
      when (elementAt) {
        is PopupFactoryImpl.ActionItem -> return elementAt.text
        is ProjectTemplate -> return elementAt.name
        else -> return elementAt.toString()
      }
    }
    return null
  }

  fun getCellText(jList: JList<*>, pointOnList: Point): String? {
    val index = jList.locationToIndex(pointOnList)
    val cellBounds = jList.getCellBounds(index, index)
    if (cellBounds.contains(pointOnList)) {
      val elementAt = jList.model.getElementAt(index)
      when (elementAt) {
        is PopupFactoryImpl.ActionItem -> return elementAt.text
        is ProjectTemplate -> return elementAt.name
        is PresentableVersion -> return elementAt.presentableName
        javaClass.canonicalName == "com.intellij.ide.util.frameworkSupport.FrameworkVersion" -> {
          val getNameMethod = elementAt.javaClass.getMethod("getVersionName")
          val name = getNameMethod.invoke(elementAt)
          return name as String
        }
        else -> return elementAt.toString()
      }
    }
    return null
  }

  fun convertSimpleTreeItemToPath(tree: SimpleTree, itemName: String): String {
    val searchableNodeRef = Ref.create<TreeNode>();
    val searchableNode: TreeNode?
    TreeUtil.traverse(tree.getModel().getRoot() as TreeNode) { node ->
      val valueFromNode = SettingsTreeFixture.getValueFromNode(tree, node)
      if (valueFromNode != null && valueFromNode == itemName) {
        assert(node is TreeNode)
        searchableNodeRef.set(node as TreeNode)
      }
      true
    }
    searchableNode = searchableNodeRef.get()
    val path = TreeUtil.getPathFromRoot(searchableNode!!)

    return (0..path.pathCount - 1).map { path.getPathComponent(it).toString() }.filter(String::isNotEmpty).joinToString("/")
  }

  fun getBoundedLabelForComboBox(cb: JComboBox<*>): JLabel {
    val robot = BasicRobot.robotWithCurrentAwtHierarchyWithoutScreenLock()

    val findBoundedLabel: (Component) -> JLabel? = { component ->
      try {
        robot.finder().find(component.parent as Container, object : GenericTypeMatcher<JLabel>(JLabel::class.java) {
          override fun isMatching(label: JLabel): Boolean {
            return label.labelFor != null && label.labelFor == component
          }
        })
      }
      catch(e: ComponentLookupException) {
        null
      }
    }

    val bounded1 = findBoundedLabel(cb)
    if (bounded1 !== null) return bounded1

    val bounded2 = findBoundedLabel(cb.parent)
    if (bounded2 !== null) return bounded2

    val bounded3 = findBoundedLabel(cb.parent!!.parent)
    if (bounded3 !== null) return bounded3

    throw ComponentLookupException("Unable to find bounded label in 2 levels from JComboBox")

  }

  fun getJTreePath(cmp: JTree, path: TreePath): String {
    var treePath = path
    val result = StringBuilder()
    val bcr = org.fest.swing.driver.BasicJTreeCellReader()
    while (treePath.pathCount != 1 || (cmp.isRootVisible && treePath.pathCount == 1)) {
      val valueAt = bcr.valueAt(cmp, treePath.lastPathComponent)
      result.insert(0, "$valueAt${if (!result.isEmpty()) "/" else ""}")
      if (treePath.pathCount == 1) break
      else treePath = treePath.parentPath
    }
    return result.toString()
  }

}

