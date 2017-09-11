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
@file:Suppress("unused")

package com.intellij.testGuiFramework.generators

import com.intellij.ide.plugins.PluginTable
import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame
import com.intellij.testGuiFramework.cellReader.ExtendedJListCellReader
import com.intellij.testGuiFramework.cellReader.ExtendedJTableCellReader
import com.intellij.testGuiFramework.driver.CheckboxTreeDriver
import com.intellij.testGuiFramework.fixtures.*
import com.intellij.testGuiFramework.fixtures.extended.ExtendedTreeFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.generators.Utils.clicks
import com.intellij.testGuiFramework.generators.Utils.convertSimpleTreeItemToPath
import com.intellij.testGuiFramework.generators.Utils.findBoundedText
import com.intellij.testGuiFramework.generators.Utils.getCellText
import com.intellij.testGuiFramework.generators.Utils.getJTreePath
import com.intellij.testGuiFramework.generators.Utils.getJTreePathArray
import com.intellij.testGuiFramework.generators.Utils.getJTreePathItemsString
import com.intellij.testGuiFramework.generators.Utils.withRobot
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.getComponentText
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.isTextComponent
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.onHeightCenter
import com.intellij.ui.CheckboxTree
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.labels.ActionLink
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.messages.SheetController
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.util.ui.tree.TreeUtil
import org.fest.reflect.core.Reflection.field
import org.fest.swing.core.BasicRobot
import org.fest.swing.core.ComponentMatcher
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.Rectangle
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

class ComponentWithBrowseButtonGenerator : ComponentCodeGenerator<FixedSizeButton> {
  override fun accept(cmp: Component): Boolean {
    return cmp.parent.parent is ComponentWithBrowseButton<*>
  }

  override fun generate(cmp: FixedSizeButton, me: MouseEvent, cp: Point): String {
    val className = cmp.parent.parent.javaClass.simpleName
    return "componentWithBrowseButton($className::class.java).clickButton()"
  }
}

class ActionButtonGenerator : ComponentCodeGenerator<ActionButton> {
  override fun accept(cmp: Component) = cmp is ActionButton
  override fun generate(cmp: ActionButton, me: MouseEvent, cp: Point): String {
    val text = cmp.action.templatePresentation.text
    val simpleClassName = cmp.action.javaClass.simpleName
    val result: String = if (text.isNullOrEmpty())
      "actionButtonByClass(\"$simpleClassName\").click()"
    else
      "actionButton(\"$text\").click()"
    return result
  }
}

class ActionLinkGenerator : ComponentCodeGenerator<ActionLink> {
  override fun priority(): Int = 1
  override fun accept(cmp: Component) = cmp is ActionLink
  override fun generate(cmp: ActionLink, me: MouseEvent, cp: Point) = "actionLink(\"${cmp.text}\").click()"
}

class JTextFieldGenerator : ComponentCodeGenerator<JTextField> {
  override fun accept(cmp: Component) = cmp is JTextField
  override fun generate(cmp: JTextField, me: MouseEvent, cp: Point) = "textfield(\"${findBoundedText(3, cmp).orEmpty()}\").${clicks(
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
    if (me.clickCount == 2) return "jList(\"$cellText\").doubleClickItem(\"$cellText\")"
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
  private fun JTree.getPath(cp: Point): TreePath = this.getClosestPathForLocation(cp.x, cp.y)
  private fun wasClickOnCheckBox(cmp: CheckboxTree, cp: Point): Boolean {
    val checkboxTree = cmp
    val treePath = cmp.getPath(cp)
    val pathArray: List<String> = getJTreePathArray(checkboxTree, treePath)
    return withRobot {
      val checkboxComponent = CheckboxTreeDriver(it).getCheckboxComponent(checkboxTree, pathArray) ?: throw Exception("Checkbox component from cell renderer is null")
      val pathBounds = checkboxTree.getPathBounds(treePath)
      val checkboxTreeBounds = Rectangle(pathBounds.x + checkboxComponent.x, pathBounds.y + checkboxComponent.y, checkboxComponent.width, checkboxComponent.height)
      checkboxTreeBounds.contains(cp)
    }
  }

  override fun generate(cmp: CheckboxTree, me: MouseEvent, cp: Point): String {
    val path = getJTreePath(cmp, cmp.getPath(cp))
    return if (wasClickOnCheckBox(cmp, cp))
      "checkboxTree($path).clickCheckbox($path)"
    else
      "checkboxTree($path).clickPath($path)"
  }
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

class JTableGenerator : ComponentCodeGenerator<JTable> {
  override fun accept(cmp: Component) = cmp is JTable

  override fun generate(cmp: JTable, me: MouseEvent, cp: Point): String {
    val row = cmp.rowAtPoint(cp)
    val col = cmp.columnAtPoint(cp)
    val cellText = ExtendedJTableCellReader().valueAt(cmp, row, col)
    return "table(\"$cellText\").cell(\"$cellText\")".addClick(me)
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
  override fun generate(cmp: JComboBox<*>, me: MouseEvent, cp: Point) = "combobox(\"${findBoundedText(3, cmp).orEmpty()}\")"
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
  override fun generate(cmp: LinkLabel<*>, me: MouseEvent, cp: Point) = "linkLabel(\"${cmp.text}\").click()"
}


class HyperlinkLabelGenerator : ComponentCodeGenerator<HyperlinkLabel> {
  override fun accept(cmp: Component) = cmp is HyperlinkLabel
  override fun generate(cmp: HyperlinkLabel, me: MouseEvent, cp: Point): String {
    //we assume, that hyperlink label has only one highlighted region
    val linkText = cmp.hightlightedRegionsBoundsMap.keys.toList().firstOrNull() ?: "null"
    return "hyperlinkLabel(\"${cmp.text}\").clickLink(\"$linkText\")"
  }
}

class HyperlinkLabelInNotificationPanelGenerator : ComponentCodeGenerator<HyperlinkLabel> {
  override fun accept(cmp: Component) = cmp is HyperlinkLabel && cmp.hasInParents(EditorNotificationPanel::class.java)
  override fun priority(): Int = 1
  override fun generate(cmp: HyperlinkLabel, me: MouseEvent, cp: Point): String {
    //we assume, that hyperlink label has only one highlighted region
    val linkText = cmp.hightlightedRegionsBoundsMap.keys.toList().firstOrNull() ?: "null"
    return "editor { notificationPanel().clickLink(\"$linkText\") }"
  }
}

class JTreeGenerator : ComponentCodeGenerator<JTree> {
  override fun accept(cmp: Component) = cmp is JTree
  private fun JTree.getPath(cp: Point) = this.getClosestPathForLocation(cp.x, cp.y)
  override fun generate(cmp: JTree, me: MouseEvent, cp: Point): String {
    val path = getJTreePath(cmp, cmp.getPath(cp))
    if (me.isRightButton()) return "jTree($path).rightClickPath($path)"
    return "jTree($path).clickPath($path)"
  }
}

class ProjectViewTreeGenerator : ComponentCodeGenerator<ProjectViewTree> {
  override fun priority() = 1
  override fun accept(cmp: Component) = cmp is ProjectViewTree
  private fun JTree.getPath(cp: Point) = this.getClosestPathForLocation(cp.x, cp.y)
  override fun generate(cmp: ProjectViewTree, me: MouseEvent, cp: Point): String {
    val path = getJTreePathItemsString(cmp, cmp.getPath(cp))
    if (me.isRightButton()) return "path($path).rightClick()"
    if (me.clickCount == 2) return "path($path).doubleClick()"
    return "path($path).click()"
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
    return withRobot { robot ->
      val resultJBPopupMenu = robot.finder().find(this, ComponentMatcher { component ->
        (component is JBPopupMenu)
        && component.isShowing
        && component.isVisible
        && !jbPopupHashSet.contains(component.hashCode())
      }) as JBPopupMenu
      jbPopupHashSet.add(resultJBPopupMenu.hashCode())
      resultJBPopupMenu
    }
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
  override fun generate(cmp: FlatWelcomeFrame): String {
    return "welcomeFrame {"
  }
}

class JDialogGenerator : GlobalContextCodeGenerator<JDialog>() {
  override fun accept(cmp: Component): Boolean {
    if (cmp !is JComponent || cmp.rootPane == null || cmp.rootPane.parent == null || cmp.rootPane.parent !is JDialog) return false
    val dialog = cmp.rootPane.parent as JDialog
    if (dialog.title == "This should not be shown") return false //do not add context for a SheetMessages on Mac
    return true

  }

  override fun generate(cmp: JDialog) = "dialog(\"${cmp.title}\") {"
}

class IdeFrameGenerator : GlobalContextCodeGenerator<JFrame>() {
  override fun accept(cmp: Component): Boolean {
    if (cmp !is JComponent) return false
    val parent = (cmp as JComponent).rootPane.parent
    return (parent is JFrame) && parent.title != "GUI Script Editor"
  }

  override fun generate(cmp: JFrame) = "ideFrame {"
}

//**********LOCAL CONTEXT GENERATORS**********

class ProjectViewGenerator : LocalContextCodeGenerator<JPanel>() {

  override fun priority() = 0
  override fun isLastContext() = true
  override fun acceptor(): (Component) -> Boolean = { component -> component.javaClass.name.endsWith("ProjectViewImpl\$MyPanel") }
  override fun generate(cmp: JPanel) = "projectView {"

}

class ToolWindowGenerator : LocalContextCodeGenerator<Component>() {

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

  override fun generate(cmp: Component): String {
    val toolWindow: ToolWindowImpl = getToolWindow(cmp.centerOnScreen())!!
    return "toolwindow(id = \"${toolWindow.id}\") {"
  }

}

class ToolWindowContextGenerator : LocalContextCodeGenerator<Component>() {

  override fun priority() = 2

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

  override fun generate(cmp: Component): String {
    val toolWindow: ToolWindowImpl = getToolWindow(cmp.centerOnScreen())!!
    val tabName = toolWindow.contentManager.selectedContent?.tabName
    return if (tabName != null) "content(tabName = \"${tabName}\") {"
    else "content {"
  }

}

class MacMessageGenerator : LocalContextCodeGenerator<JButton>() {

  override fun priority() = 2

  private fun acceptMacSheetPanel(cmp: Component): Boolean {
    if (cmp !is JComponent) return false
    if (!(Messages.canShowMacSheetPanel() && cmp.rootPane.parent is JDialog)) return false
    val panel = cmp.rootPane.contentPane as JPanel

    if (panel.javaClass.name.startsWith(SheetController::class.java.name) && panel.isShowing) {
      val controller = MessagesFixture.findSheetController(panel)
      val sheetPanel = field("mySheetPanel").ofType(JPanel::class.java).`in`(controller).get()
      if (sheetPanel === panel) {
        return true
      }
    }
    return false
  }

  override fun acceptor(): (Component) -> Boolean = { component -> acceptMacSheetPanel(component) }

  override fun generate(cmp: JButton): String {
    val panel = cmp.rootPane.contentPane as JPanel
    val title = withRobot { robot -> MessagesFixture.getTitle(panel, robot) }
    return "message(\"$title\") {"
  }
}

class MessageGenerator : LocalContextCodeGenerator<JDialog>() {

  override fun priority() = 2

  override fun acceptor(): (Component) -> Boolean = { cmp ->
    cmp is JDialog && MessageDialogFixture.isMessageDialog(cmp, Ref<DialogWrapper>())
  }

  override fun generate(cmp: JDialog): String {
    return "message(\"${cmp.title}\") {"
  }
}

class EditorGenerator : LocalContextCodeGenerator<EditorComponentImpl>() {

  override fun acceptor(): (Component) -> Boolean = { component -> component is EditorComponentImpl }
  override fun generate(cmp: EditorComponentImpl) = "editor {"

}

class MainToolbarGenerator : LocalContextCodeGenerator<ActionToolbarImpl>() {
  override fun acceptor(): (Component) -> Boolean = { component ->
    component is ActionToolbarImpl
    && MainToolbarFixture.isMainToolbar(component)
  }

  override fun generate(cmp: ActionToolbarImpl): String = "toolbar {"
}

class NavigationBarGenerator : LocalContextCodeGenerator<JPanel>() {
  override fun acceptor(): (Component) -> Boolean = { component ->
    component is JPanel
    && NavigationBarFixture.isNavBar(component)
  }

  override fun generate(cmp: JPanel): String = "navigationBar {"
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
    return withRobot { robot -> GuiTestUtil.findBoundedLabel(container, jTextField, robot) }
  }

  fun getLabel(jTextField: JTextField): JLabel? {
    val parentContainer = jTextField.rootPane.parent
    return withRobot { robot -> GuiTestUtil.findBoundedLabel(parentContainer, jTextField, robot) }
  }

  fun clicks(me: MouseEvent): String {
    if (me.clickCount == 1) return "click()"
    if (me.clickCount == 2) return "doubleClick()"
    return ""
  }

  fun getCellText(jList: JList<*>, pointOnList: Point): String? {
    return withRobot { robot ->
      val extCellReader = ExtendedJListCellReader()
      val index = jList.locationToIndex(pointOnList)
      extCellReader.valueAt(jList, index)
    }
  }

  fun convertSimpleTreeItemToPath(tree: SimpleTree, itemName: String): String {
    val searchableNodeRef = Ref.create<TreeNode>()
    val searchableNode: TreeNode?
    TreeUtil.traverse(tree.model.root as TreeNode) { node ->
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

  /**
   * @hierarchyLevel: started from 1 to see bounded label for a component itself
   */
  fun getBoundedLabel(hierarchyLevel: Int, component: Component): JLabel {

    var currentComponentParent = component.parent
    if (hierarchyLevel < 1) throw Exception(
      "Hierarchy level (actual is $hierarchyLevel) should starts from 1 to see bounded label for a component itself")

    for (i in 1..hierarchyLevel) {
      val boundedLabel = findBoundedLabel(component, currentComponentParent.parent)
      if (boundedLabel != null) return boundedLabel
      else {
        if (currentComponentParent.parent == null) break
        currentComponentParent = currentComponentParent.parent
      }
    }

    throw ComponentLookupException("Unable to find bounded label in ${hierarchyLevel - 1} level(s) from $component")

  }

  private fun findBoundedLabel(component: Component, componentParent: Component): JLabel? {
    return withRobot { robot ->
      var resultLabel: JLabel?
      if (componentParent is LabeledComponent<*>) resultLabel = componentParent.label
      else {
        try {
          resultLabel = robot.finder().find(componentParent as Container, object : GenericTypeMatcher<JLabel>(JLabel::class.java) {
            override fun isMatching(label: JLabel) = (label.labelFor != null && label.labelFor == component)
          })
        }
        catch(e: ComponentLookupException) {
          resultLabel = null
        }
      }
      resultLabel
    }
  }


  fun findBoundedText(hierarchyLevel: Int, target: Component): String? {
    //let's try to find bounded label firstly
    try {
      return getBoundedLabel(hierarchyLevel, target).text
    }
    catch (e: ComponentLookupException) {
      //do nothing
    }

    var container = target.parent
    for (i in 1..hierarchyLevel) {
      val boundedText = findBoundedText(target, container)
      if (boundedText != null)
        return boundedText
      else
        container = container.parent ?: break
    }
    return null
//    throw ComponentLookupException("Unable to find any bounded label (JLabel or JRadioButton) in $hierarchyLevel level(s) from $target component")
  }

  fun findBoundedText(target: Component, container: Component): String? {
    val textComponents = withRobot { robot ->
      robot.finder().findAll(container as Container,
                             ComponentMatcher { component -> component!!.isTextComponent() && target.onHeightCenter(component, true) })
    }
    if (textComponents.isEmpty()) return null
    //if  more than one component is found let's take the righter one
    return textComponents.sortedBy { it.bounds.x + it.bounds.width }.last().getComponentText()
  }

  fun getJTreePath(cmp: JTree, path: TreePath): String {
    val pathArray = getJTreePathArray(cmp, path)
    return pathArray.joinToString(separator = ", ", transform = { str -> "\"$str\"" })
  }

  fun getJTreePathItemsString(cmp: JTree, path: TreePath): String {
    return getJTreePathArray(cmp, path)
      .map { StringUtil.wrapWithDoubleQuote(it) }
      .reduceRight({ s, s1 -> "$s, $s1" })
  }

  internal fun getJTreePathArray(tree: JTree, path: TreePath): List<String>
    = withRobot { robot -> ExtendedTreeFixture(robot, tree).getPath(path) }

  fun <ReturnType> withRobot(robotFunction: (Robot) -> ReturnType): ReturnType {
    val robot = BasicRobot.robotWithCurrentAwtHierarchyWithoutScreenLock()
    val result = robotFunction(robot)
    return result
  }

}

private fun String.addClick(me: MouseEvent): String {
  return when {
    me.isLeftButton() && me.clickCount == 2 -> "$this.doubleClick()"
    me.isRightButton() -> "$this.rightClick()"
    else -> "$this.click()"
  }
}

private fun Component.hasInParents(componentType: Class<out Component>): Boolean {
  var component = this
  while(component.parent != null) {
    if (componentType.isInstance(component)) return true
    component = component.parent
  }
  return false
}

