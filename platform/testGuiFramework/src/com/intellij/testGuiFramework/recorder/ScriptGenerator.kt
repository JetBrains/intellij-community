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
package com.intellij.testGuiFramework.recorder

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Ref
import com.intellij.openapi.wm.WindowManager
import com.intellij.testGuiFramework.fixtures.SettingsTreeFixture
import com.intellij.testGuiFramework.generators.ComponentCodeGenerator
import com.intellij.testGuiFramework.generators.Generators
import com.intellij.testGuiFramework.recorder.ScriptGenerator.scriptBuffer
import com.intellij.testGuiFramework.recorder.components.GuiRecorderComponent
import com.intellij.testGuiFramework.recorder.ui.KeyUtil
import com.intellij.ui.KeyStrokeAdapter
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Component
import java.awt.Menu
import java.awt.MenuItem
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTree
import javax.swing.KeyStroke.getKeyStrokeForEvent
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/**
 * @author Sergey Karashevich
 */
object ScriptGenerator {

  val scriptBuffer = StringBuilder("")

  var openComboBox = false
  fun getScriptBuffer() = scriptBuffer.toString()
  fun clearScriptBuffer() = scriptBuffer.setLength(0)

  private val generators: List<ComponentCodeGenerator<*>> = Generators.getGenerators()

  object ScriptWrapper {

    val TEST_METHOD_NAME = "testMe"

    private fun classWrap(function: () -> (String)): String = "class CurrentTest: GuiTestCase() {\n${function.invoke()}\n}"
    private fun funWrap(function: () -> String): String = "fun $TEST_METHOD_NAME(){\n${function.invoke()}\n}"

    private fun importsWrap(vararg imports: String, function: () -> String): String {
      val sb = StringBuilder()
      imports.forEach { sb.append("$it\n") }
      sb.append(function.invoke())
      return sb.toString()
    }

    fun wrapScript(code: String): String =
      importsWrap(
        "import com.intellij.testGuiFramework.* ",
        "import com.intellij.testGuiFramework.fixtures.*",
        "import com.intellij.testGuiFramework.framework.*",
        "import com.intellij.testGuiFramework.impl.*",
        "import org.fest.swing.core.Robot",
        "import java.awt.Component",
        "import com.intellij.openapi.application.ApplicationManager",
        "import org.fest.swing.fixture.*")
      {
        classWrap {
          funWrap {
            code
          }
        }
      }
  }


//    fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent?) {
//        get action type for script: click, enter text, mark checkbox
//        val component = e!!.getDataContext().getData("Component") as Component
//        checkGlobalContext(component)
//        clickCmp(component, e)
//    }

  fun processTyping(keyChar: Char) {
    Typer.type(keyChar)
  }

  //(keyEvent.id == KeyEvent.KEY_PRESSED) for all events here
  fun processKeyPressing(keyEvent: KeyEvent) {
    //retrieve shortcut here
//        val keyStroke = getKeyStrokeForEvent(keyEvent)
//        val actionIds = KeymapManager.getInstance().activeKeymap.getActionIds(keyStroke)
//        if (!actionIds.isEmpty()) {
//            val firstActionId = actionIds[0]
//            if (IgnoredActions.ignore(firstActionId)) return
//            val keyStrokeStr = KeyStrokeAdapter.toString(keyStroke)
//            if (IgnoredActions.ignore(keyStrokeStr)) return
//            Writer.writeln(Templates.invokeActionComment(firstActionId))
//            makeIndent()
//            Writer.writeln(Templates.shortcut(keyStrokeStr))
//        }
  }

  fun processKeyActionEvent(action: AnAction, event: AnActionEvent) {
    ScriptGenerator.flushTyping()

    val keyEvent = event.inputEvent as KeyEvent
    val actionId = event.actionManager.getId(action)
    if(actionId != null) {
      if (IgnoredActions.ignore(actionId)) return
      addToScript(Templates.invokeActionComment(actionId))
    }

    val keyStroke = getKeyStrokeForEvent(keyEvent)
    val keyStrokeStr = KeyStrokeAdapter.toString(keyStroke)
    if (IgnoredActions.ignore(keyStrokeStr)) return
    addToScript(Templates.shortcut(keyStrokeStr))
  }

  //    clickComponent methods
  fun clickComponent(component: Component, convertedPoint: Point, me: MouseEvent) {
    awareListsAndPopups(component) {
      ContextChecker.checkContext(component, me, convertedPoint)
//            checkGlobalContext(component, me, convertedPoint)
//            checkLocalContext(component, me, convertedPoint)
    }

    val suitableGenerator = generators.filter { generator -> generator.accept(component) }.sortedByDescending(
      ComponentCodeGenerator<*>::priority).firstOrNull() ?: return
    val code = suitableGenerator.generateCode(component, me, convertedPoint)
    addToScript(code)
  }

//

  fun awareListsAndPopups(cmp: Component, body: () -> Unit) {
    cmp as JComponent
    if (cmp is JList<*> && openComboBox) return //don't change context for comboBox list
    if (isPopupList(cmp)) return //dont' change context for a popup menu
    body()
  }

  fun clearContext() {
    ContextChecker.clearContext()
  }


  fun processMainMenuActionEvent(anActionToBePerformed: AnAction, anActionEvent: AnActionEvent) {
    val actionId: String? = ActionManager.getInstance().getId(anActionToBePerformed)
    if (actionId == null) {
      addToScript("//called action (${anActionToBePerformed.templatePresentation.text}) from main menu with null actionId"); return
    }
    addToScript(Templates.invokeMainMenuAction(actionId))
  }


  fun flushTyping() {
    Typer.flushBuffer()
  }

  private fun isPopupList(cmp: Component) = cmp.javaClass.name.toLowerCase().contains("listpopup")
  private fun isFrameworksTree(cmp: Component) = cmp.javaClass.name.toLowerCase().contains("AddSupportForFrameworksPanel".toLowerCase())


  private fun Component.inToolWindow(): Boolean {
    var pivotComponent = this
    while (pivotComponent.parent != null) {
      if (pivotComponent is SimpleToolWindowPanel) return true
      else pivotComponent = pivotComponent.parent
    }
    return false
  }

  fun addToScript(code: String, withIndent: Boolean = true, indent: Int = 2) {
    if (withIndent) {
      val indentedString = (0..(indent * ContextChecker.getContextDepth() - 1)).map { i -> ' ' }.joinToString(separator = "")
      ScriptGenerator.addToScriptDelegate("$indentedString$code")
    }
    else ScriptGenerator.addToScriptDelegate(code)
  }

  //use it for outer generators
  private fun addToScriptDelegate(code: String?) {
    if (code != null) Writer.writeln(code)
  }

}


object Writer {

  fun writeln(str: String) {
    write(str + "\n")
  }

  fun write(str: String) {
    writeToConsole(str)
    if (GuiRecorderComponent.getFrame() != null && GuiRecorderComponent.getFrame()!!.isSyncToEditor())
      writeToEditor(str)
    else
      writeToBuffer(str)
  }

  fun writeToConsole(str: String) {
    print(str)
  }

  fun writeToBuffer(str: String) {
    scriptBuffer.append(str)
  }

  fun writeToEditor(str: String) {
    if (GuiRecorderComponent.getFrame() != null && GuiRecorderComponent.getFrame()!!.getEditor() != null) {
      val editor = GuiRecorderComponent.getFrame()!!.getEditor()
      val document = editor.document
//            ApplicationManager.getApplication().runWriteAction { document.insertString(document.textLength, str) }
      WriteCommandAction.runWriteCommandAction(null, { document.insertString(document.textLength, str) })
    }
  }
}

private object Typer {
  val strBuffer = StringBuilder()
  val rawBuffer = StringBuilder()

  fun type(keyChar: Char) {
    strBuffer.append(KeyUtil.patch(keyChar))
    rawBuffer.append("${if (rawBuffer.length > 0) ", " else ""}\"${keyChar.toInt()}\"")
  }

  fun flushBuffer() {
    if (strBuffer.length == 0) return
    ScriptGenerator.addToScript("//typed:[${strBuffer.length},\"${strBuffer.toString()}\", raw=[${rawBuffer.toString()}]]")
    ScriptGenerator.addToScript(Templates.typeText(strBuffer.toString()))
    strBuffer.setLength(0)
    rawBuffer.setLength(0)
  }
}

//TEMPLATES


object IgnoredActions {

  val ignoreActions = listOf("EditorBackSpace")
  val ignoreShortcuts = listOf("space")

  fun ignore(actionOrShortCut: String): Boolean = (ignoreActions.contains(actionOrShortCut) || ignoreShortcuts.contains(actionOrShortCut))
}

object Util {

//    fun isActionFromMainMenu(anActionTobePerformed: AnAction, anActionEvent: AnActionEvent): Boolean {
//        val menuBar = WindowManager.getInstance().findVisibleFrame().menuBar ?: return false
//    }

  fun getPathFromMainMenu(anActionTobePerformed: AnAction, anActionEvent: AnActionEvent): String? {
//        WindowManager.getInstance().findVisibleFrame().menuBar.getMenu(0).label
    val menuBar = WindowManager.getInstance().findVisibleFrame().menuBar ?: return null
    //in fact it should be only one String in "map"
    return (0..(menuBar.menuCount - 1)).mapNotNull {
      traverseMenu(menuBar.getMenu(it), anActionTobePerformed.templatePresentation.text!!)
    }.lastOrNull()
  }

  fun traverseMenu(menuItem: MenuItem, itemName: String): String? {
    if (menuItem is Menu) {
      if (menuItem.itemCount == 0) {
        if (menuItem.label == itemName) return itemName
        else return null
      }
      else {
        (0..(menuItem.itemCount - 1))
          .mapNotNull { traverseMenu(menuItem.getItem(it), itemName) }
          .forEach { return "${menuItem.label};$it" }
        return null
      }
    }
    else {
      if (menuItem.label == itemName) return itemName
      else return null
    }
  }

  fun convertSimpleTreeItemToPath(tree: SimpleTree, itemName: String): String {
    val searchableNodeRef = Ref.create<TreeNode>()
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