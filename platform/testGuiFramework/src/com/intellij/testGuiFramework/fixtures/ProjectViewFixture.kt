// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testGuiFramework.cellReader.ExtendedJTreeCellReader
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.computeOnEdt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.isComponentShowing
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.repeatUntil
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.runOnEdt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.tryWithPause
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitUntil
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.withPauseWhenNull
import com.intellij.ui.LoadingNode
import com.intellij.util.ui.tree.TreeUtil
import org.fest.assertions.Assertions.assertThat
import org.fest.reflect.core.Reflection.field
import org.fest.swing.cell.JTreeCellReader
import org.fest.swing.core.MouseButton
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiActionRunner
import org.fest.swing.edt.GuiTask
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.timing.Timeout
import org.junit.Assert.assertNotNull
import java.awt.Point
import java.awt.Rectangle
import java.util.*
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import kotlin.collections.ArrayList

class ProjectViewFixture internal constructor(project: Project, robot: Robot) : ToolWindowFixture("Project", project, robot) {

  val nodeReader: JTreeCellReader = ExtendedJTreeCellReader()

  private fun selectProjectPane(): PaneFixture = getPaneById("ProjectPane")
  fun selectAndroidPane(): PaneFixture = getPaneById("AndroidView")

  private fun getPaneById(id: String): PaneFixture {
    activate()
    val projectView = computeOnEdt { ProjectView.getInstance(myProject) } ?: throw Exception("Unable to compute ProjectView on EDT")
    assertProjectViewIsInitialized(projectView)
    runOnEdt { projectView.changeView(id) }
    return PaneFixture(projectView.getProjectViewPaneById(id))
  }

  private fun assertProjectViewIsInitialized(projectView: ProjectView) {
    GuiTestUtilKt.waitUntil("Project view is initialized", Timeouts.defaultTimeout) {
      field("isInitialized").ofType(Boolean::class.javaPrimitiveType!!).`in`(projectView).get() ?: throw Exception(
        "Unable to get 'isInitialized' field from projectView")
    }
  }

  /**
   * @param pathTo could be a separate vararg of String objects like ["project_name", "src", "Test.java"] or one String with a path
   * separated by slash sign: ["project_name/src/Test.java"]
   * @return NodeFixture object for a pathTo; may be used for expanding, scrolling and clicking node
   */
  fun path(vararg pathTo: String, timeout: Timeout = Timeouts.seconds30): NodeFixture {
    val projectPane = selectProjectPane()
    val canonicalPath = pathTo.toList().expandSlashedPath()
    return tryWithPause(exceptionClass = ComponentLookupException::class.java,
                        condition = "node with path ${Arrays.toString(pathTo)} will appear",
                        timeout = timeout) {
      activate()
      projectPane.getNode(canonicalPath)
    }
  }


  private fun List<String>.expandSlashedPath(): List<String> {
    return if (this.size == 1 && this[0].contains("/")) {
      this[0].split("/".toRegex()).dropLastWhile { it.isEmpty() }
    }
    else this
  }


  inner class PaneFixture internal constructor(private val myPane: AbstractProjectViewPane) {

    fun expand(): PaneFixture {
      GuiActionRunner.execute(object : GuiTask() {
        @Throws(Throwable::class)
        override fun executeInEDT() {
          TreeUtil.expandAll(myPane.tree)
        }
      })
      return this
    }

    fun getNode(path: List<String>): NodeFixture {
      val tree = myPane.tree
      val root = computeOnEdt { myPane.tree.model.root } ?: throw Exception("The root for a tree model in ProjectView is null")
      val pathToNode = traverseChildren(tree, TreePath(root), path)
      return NodeFixture(pathToNode.lastPathComponent as DefaultMutableTreeNode, pathToNode, myPane)
    }

    private fun traverseChildren(tree: JTree, treePath: TreePath, path: List<String>): TreePath {
      val model = tree.model
      val parent = treePath.lastPathComponent
      val childCount: Int = computeOnEdt { model.getChildCount(parent) } ?: throw Exception("Unable to calculate children for ${path[0]}")
      expandIfLoadingNode(childCount, model, parent, tree, treePath)

      for (i in (0 until childCount)){
        val child = computeOnEdt { model.getChild(parent, i) }
        if (nodeReader.valueAt(tree, child) == path[0]) {
          return if (path.size == 1)
             treePath.pathByAddingChild(child)
          else
             traverseChildren(tree, treePath.pathByAddingChild(child), path.drop(1))
        }
      }
      throw ComponentLookupException("Unable to find child with name '${path[0]}'")
    }

    private fun expandIfLoadingNode(childCount: Int,
                                    model: TreeModel,
                                    parent: Any?,
                                    tree: JTree,
                                    treePath: TreePath) {
      if (childCount == 1) {
        val singleChild = computeOnEdt { model.getChild(parent, 0) }
        if (singleChild is LoadingNode) {
          runOnEdt { TreeUtil.selectPath(tree, treePath.pathByAddingChild(singleChild)) }
          waitUntil("children will be loaded", Timeouts.seconds30) {
            computeOnEdt { model.getChildCount(parent) > 1 || model.getChild(parent, 0) !is LoadingNode }!!
          }
        }
      }
    }
  }

  inner class NodeFixture internal constructor(private val myNode: DefaultMutableTreeNode,
                                               private val myTreePath: TreePath,
                                               private val myPane: AbstractProjectViewPane) {

    val location: Point
      get() {
        val tree = myPane.tree
        val boundsRef = Ref<Rectangle>()
        waitUntil("bounds of tree node with a tree path $myTreePath will be not null", Timeouts.defaultTimeout) {
          val bounds = computeOnEdt { tree.getPathBounds(myTreePath) }
          if (bounds != null) boundsRef.set(bounds)
          return@waitUntil bounds != null
        }

        val bounds = boundsRef.get()
        return Point(bounds.x + bounds.height / 2, bounds.y + bounds.height / 2)
      }

    private val locationOnScreen: Point
      get() {
        val locationOnScreen = myPane.componentToFocus.locationOnScreen
        val location = location
        return Point(locationOnScreen.x + location.x, locationOnScreen.y + location.y)
      }

    fun click() {
      expand()
      myRobot.click(locationOnScreen, MouseButton.LEFT_BUTTON, 1)
    }

    fun doubleClick() {
      expand()
      myRobot.click(locationOnScreen, MouseButton.LEFT_BUTTON, 2)
    }

    fun rightClick() {
      repeatUntil({ isComponentShowing(JPopupMenu::class.java) }, {
        invokeContextMenu()
      })
    }

    fun invokeContextMenu() {
      expand()
      myRobot.waitForIdle()
      myRobot.click(locationOnScreen, MouseButton.RIGHT_BUTTON, 1)
    }

    fun requireDirectory(name: String): NodeFixture {
      val projectViewNode = myNode.userObject
      assertThat(projectViewNode).isInstanceOf(PsiDirectoryNode::class.java)
      val file = (projectViewNode as PsiDirectoryNode).virtualFile
      assertNotNull(file)
      assertThat(file!!.name).isEqualTo(name)
      return this
    }

    private fun createPath(ats: AbstractTreeStructure, node: Any): Array<Any> {
      val buildPath = ArrayList<Any>()
      val root = ats.rootElement
      var currentNode: Any = node
      while (currentNode !== root) {
        buildPath.add(0, currentNode)
        currentNode = ats.getParentElement(currentNode) ?: throw Exception(
          "Suddenly the current node is zero, although the parent was not reached")
      }
      buildPath.add(0, currentNode)
      return buildPath.toTypedArray()
    }


    override fun toString(): String {
      val projectViewNode = myNode.userObject as ProjectViewNode<*>
      return StringUtil.notNullize(projectViewNode.name)
    }

    //expands and scrolls to the myTreePath of the current node
    fun expand(): NodeFixture {
      runOnEdt {
        TreeUtil.selectPath(myPane.tree, myTreePath)
      }
      return this
    }

    private fun expandPathIncludingLeafs(tree: JTree, treePath: TreePath) {
      val pathToBeExpanded = if (tree.model.isLeaf(treePath.lastPathComponent)) treePath.parentPath else treePath
      tree.expandPath(pathToBeExpanded)
    }

  }

  companion object {

    private val LOG = Logger.getInstance(ProjectViewFixture::class.java)

    private fun getNodeText(node: Any): String? {
      assert(node is PresentableNodeDescriptor<*>)
      val descriptor = node as PresentableNodeDescriptor<*>
      runOnEdt { descriptor.update() }
      return descriptor.presentation.presentableText
    }
  }
}

