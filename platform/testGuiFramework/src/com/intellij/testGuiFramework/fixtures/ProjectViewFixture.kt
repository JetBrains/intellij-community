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
package com.intellij.testGuiFramework.fixtures

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testGuiFramework.framework.GuiTestUtil.SHORT_TIMEOUT
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.computeOnEdt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.runOnEdt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitUntil
import com.intellij.ui.LoadingNode
import com.intellij.util.ui.tree.TreeUtil
import org.fest.assertions.Assertions.assertThat
import org.fest.reflect.core.Reflection.field
import org.fest.swing.core.MouseButton
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiActionRunner
import org.fest.swing.edt.GuiTask
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.junit.Assert.assertNotNull
import java.awt.Point
import java.awt.Rectangle
import java.util.*
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import kotlin.collections.ArrayList

class ProjectViewFixture internal constructor(project: Project, robot: Robot) : ToolWindowFixture("Project", project, robot) {

  fun selectProjectPane(): PaneFixture = getPaneById("ProjectPane")
  fun selectAndroidPane(): PaneFixture = getPaneById("AndroidView")

  private fun getPaneById(id: String): PaneFixture {
    activate()
    val projectView = ProjectView.getInstance(myProject)
    assertProjectViewIsInitialized(projectView)
    runOnEdt { projectView.changeView(id) }
    return PaneFixture(projectView.getProjectViewPaneById(id))
  }

  private fun assertProjectViewIsInitialized(projectView: ProjectView) {
    GuiTestUtilKt.waitUntil("Project view is initialized", 120) {
      field("isInitialized").ofType(Boolean::class.javaPrimitiveType!!).`in`(projectView).get() ?: throw Exception(
        "Unable to get 'isInitialized' field from projectView")
    }
  }

  fun assertStructure(expectedStructure: String) {
    val printInfo = Queryable.PrintInfo()
    runOnEdt {
//      ProjectViewTestUtil.assertStructureEqual(treeStructure, expectedStructure, StringUtil.countNewLines(expectedStructure) + 1,
//                                               PlatformTestUtil.createComparator(printInfo), treeStructure.rootElement, printInfo)
    }
  }

  fun assertAsyncStructure(expectedStructure: String) {
    val lastThrowable = Ref<Throwable>()
    val timeout = SHORT_TIMEOUT
    try {
      waitUntil("Waiting for project view update", 120) {
        val printInfo = Queryable.PrintInfo()
        computeOnEdt {
          try {
            //todo: replace AbstractTreeStructure
//            ProjectViewTestUtil.assertStructureEqual(treeStructure, expectedStructure, StringUtil.countNewLines(expectedStructure) + 1,
//                                                     PlatformTestUtil.createComparator(printInfo), treeStructure.rootElement,
//                                                     printInfo)
          }
          catch (ae: AssertionError) {
            lastThrowable.set(ae)
          }
          true
        }!!
      }
    }
    catch (error: WaitTimedOutError) {
      val throwable = lastThrowable.get()
      if (throwable != null) {
        throw AssertionError(
          "Failed on waiting project structure update for " + timeout.toString() + ", expected and actual structures are still different: ",
          throwable)
      }
      else {
        throw error
      }
    }

  }

  /**
   * @param pathTo could be a separate vararg of String objects like ["project_name", "src", "Test.java"] or one String with a path
   * separated by slash sign: ["project_name/src/Test.java"]
   * @return NodeFixture object for a pathTo; may be used for expanding, scrolling and clicking node
   */
  fun path(vararg pathTo: String): NodeFixture {
    val projectPaneFixture: PaneFixture = selectProjectPane()
    val nodeFixtureRef = Ref<NodeFixture>()
    try {
      waitUntil("node by path $pathTo will appear", 30) {
        try {
          val nodeFixtureByPath = getNodeFixtureByPath(pathTo as Array<String>)
          nodeFixtureRef.set(nodeFixtureByPath)
          nodeFixtureByPath != null
        }
        catch (e: java.lang.Exception) {
          false
        }
      }
    }
    catch (timedOutError: WaitTimedOutError) {
      LOG.error("Unable to find path: ${Arrays.toString(pathTo)} for current project structure.", timedOutError)
    }
    //expand path to the node and rebuild NodeFixture if it left tree
    return nodeFixtureRef.get()
  }

  private fun getNodeFixtureByPath(pathTo: Array<String>): NodeFixture? {
    if (pathTo.size == 1) {
      if (pathTo[0].contains("/")) {
        val newPath = pathTo[0].split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return selectProjectPane().getNode(newPath)
      }
    }
    return selectProjectPane().getNode(pathTo)
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

    fun getPane() = myPane

    fun getNode(path: Array<String>): NodeFixture? {
      val tree = myPane.tree
      val root = computeOnEdt { myPane.tree.model.root } ?: throw Exception("Unfortunately the root for a tree model in ProjectView is null")
      var pivotRoot: Any = root
      for (pathItem in path) {
        var (childCount, children) = getChildrenAndCountOnEdt(tree, pivotRoot)
        if (childCount == 0) throw Exception("${pathItem} node has no more children")
        if (childCount == 1 && children[0] is LoadingNode) {
          runOnEdt { TreeUtil.selectPath(tree, TreeUtil.getPathFromRoot(children[0]!!)) }
          waitUntil("children will be loaded", 30) {
            val updatedChildrenAndCount = getChildrenAndCountOnEdt(tree, pivotRoot)
            childCount = updatedChildrenAndCount.first
            children = updatedChildrenAndCount.second
            childCount > 1 || (childCount == 1 && children[0] !is LoadingNode)
          }
        }
        var childIsFound = false
        for (child in children) {
          child ?: throw Exception("Path element ($pathItem) is null")
          val nodeText = getNodeText(child.userObject)
          nodeText ?: throw AssertionError("Unable to get text of project view node for pathItem: " + pathItem)
          if (nodeText == pathItem) {
            pivotRoot = child
            childIsFound = true
            break
          }
        }
        if (!childIsFound) return null
      }
      return NodeFixture(pivotRoot as DefaultMutableTreeNode, TreeUtil.getPathFromRoot(pivotRoot), myPane)
    }

    private fun getChildrenAndCountOnEdt(tree: JTree,
                                         node: Any): Pair<Int, ArrayList<DefaultMutableTreeNode?>> {
      return computeOnEdt {
        Pair(tree.model.getChildCount(node),
             (0 until tree.model.getChildCount(node))
               .map { tree.model.getChild(node, it) as DefaultMutableTreeNode? }
               .toCollection(arrayListOf<DefaultMutableTreeNode?>()))
      }!!
    }
  }

  inner class NodeFixture internal constructor(private val myNode: DefaultMutableTreeNode,
                                               private val myTreePath: TreePath,
                                               private val myPane: AbstractProjectViewPane) {

    val location: Point
      get() {
        val tree = myPane.tree
        val boundsRef = Ref<Rectangle>()
        waitUntil("bounds of tree node with a tree path $myTreePath will be not null", 120) {
          return@waitUntil computeOnEdt {
            val bounds = tree.getPathBounds(myTreePath)
            if (bounds != null) boundsRef.set(bounds)
            bounds != null
          }!!
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
      invokeContextMenu()
    }

    fun invokeContextMenu() {
      expand()
      myRobot.click(locationOnScreen, MouseButton.RIGHT_BUTTON, 1)
    }

    val isJdk: Boolean
      get() {
        if (myNode is NamedLibraryElementNode) {
          val value = myNode.value
          assertNotNull(value)
          val orderEntry = value!!.orderEntry
          if (orderEntry is JdkOrderEntry) {
            val sdk = orderEntry.jdk
            return sdk.sdkType is JavaSdk
          }
        }
        return false
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

    private val LOG = Logger.getInstance("#com.intellij.testGuiFramework.fixtures.ProjectViewFixture")

    private fun getNodeText(node: Any): String? {
      assert(node is PresentableNodeDescriptor<*>)
      val descriptor = node as PresentableNodeDescriptor<*>
      descriptor.update()
      return descriptor.presentation.presentableText
    }
  }
}

