// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.CACHED_TREE_PRESENTATION_PROPERTY
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.Invoker
import com.intellij.util.concurrency.InvokerSupplier
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.await
import java.beans.PropertyChangeListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

internal class TreeStateTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false // otherwise coroutines deadlock

  fun `test restore cached presentation - same state`() {
    cachedPresentationTest(
      inputToSave = """
       +root
        +a1
         *a1.1
        -a2
         *a2.1
         *a2.2
        *a3
      """.trimIndent(),
      expectedLoadedNodes = listOf(
        "/root",
        "/root/a1",
        "/root/a1/a1.1",
        "/root/a2",
        "/root/a3",
      )
    )
  }

  fun `test restore cached presentation with deeper children - same state`() {
    cachedPresentationTest(
      inputToSave = """
       +root
        +a1
         -a1.1
          *a1.1.1
          *a1.1.2
        -a2
         *a2.1
         *a2.2
        *a3
      """.trimIndent(),
      expectedLoadedNodes = listOf(
        "/root",
        "/root/a1",
        "/root/a1/a1.1",
        "/root/a2",
        "/root/a3",
      )
    )
  }

  fun `test restore cached presentation - no children to restore`() {
    cachedPresentationTest(
      inputToSave = """
       +root
        +a1
         *a1.1
        -a2
         *a2.1
         *a2.2
        *a3
      """.trimIndent(),
      inputToRestore = """
       +root
        +a1
        -a2
         *a2.1
         *a2.2
        *a3
      """.trimIndent(),
      expectedLoadedNodes = listOf(
        "/root",
        "/root/a1",
        "/root/a2",
        "/root/a3",
      )
    )
  }

  private fun cachedPresentationTest(
    inputToSave: String,
    inputToRestore: String = inputToSave,
    expectedLoadedNodes: List<String>,
  ) = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val coroutineScope = this
    val tree = createTree(inputToSave, coroutineScope)
    TreeUtil.promiseExpand(tree, Int.MAX_VALUE) { treePath ->
      (TreeUtil.getLastUserObject(treePath) as? UserObject)?.value?.isInitiallyExpanded == true
    }.await()
    val state = TreeState.createOn(tree, true, false, true)
    val newTree = createTree(inputToRestore, coroutineScope)
    state.applyTo(newTree)
    val actualLoadedNodes = suspendCancellableCoroutine { continuation -> 
      newTree.addPropertyChangeListener(CACHED_TREE_PRESENTATION_PROPERTY, PropertyChangeListener {
        if (newTree.cachedPresentation == null) {
          continuation.resume(collectVisibleLoadedPaths(newTree))
        }
      })
    }
    assertThat(actualLoadedNodes).isEqualTo(expectedLoadedNodes)
  }

  private fun parseStructure(input: String) = Parser(project, input).parse()

  private fun createTree(input: String, coroutineScope: CoroutineScope): Tree {
    val root = parseStructure(input)
    val bgtModel = createBgtModel(root, coroutineScope)
    val edtModel = AsyncTreeModel(bgtModel, coroutineScope.asDisposable())
    return Tree(edtModel)
  }

  private fun collectVisibleLoadedPaths(tree: Tree): List<String> {
    val result = mutableListOf<String>()
    val model = tree.model
    val root = model.root
    collectVisibleLoadedPaths(result, tree, model, null, listOf(root))
    return result
  }

  private fun collectVisibleLoadedPaths(result: MutableList<String>, tree: Tree, model: TreeModel, prefix: TreePath?, nodes: List<Any>) {
    for (node in nodes) {
      val userObject = TreeUtil.getUserObject(node)
      if (userObject !is UserObject) continue // not loaded
      val path = if (prefix == null) CachingTreePath(node) else prefix.pathByAddingChild(node)
      result += path.toTestString()
      if (tree.isExpanded(path)) {
        collectVisibleLoadedPaths(result, tree, model, path, model.getChildren(node))
      }
    }
  }
}

private fun TreePath.toTestString(): String {
  val thisString = (TreeUtil.getUserObject(lastPathComponent) as UserObject).value.text
  return if (parentPath == null) {
    "/$thisString"
  }
  else {
    "${parentPath.toTestString()}/$thisString"
  }
}

private fun TreeModel.getChildren(node: Any): List<Any> = (0 until getChildCount(node)).map { getChild(node, it) }

private fun createBgtModel(root: UserObject, coroutineScope: CoroutineScope) =
  BackendTreeModel(root, coroutineScope)

private class Parser(private val project: Project, private val input: String) {
  val lines = input.split("\n")
  var line = 0
  var level = 0

  fun parse(): UserObject {
    return parseChildren().single()
  }

  private fun parseChildren(): List<UserObject> {
    val result = mutableListOf<UserObject>()
    while (line < lines.size) {
      val currentLine = lines[line]
      val currentLineLevel = currentLine.indexOfFirst { !it.isWhitespace() }
      if (currentLineLevel < level) break // no children at this level
      if (currentLineLevel > level) {
        throw IllegalArgumentException("Either a parser bug or invalid input: line=$line, level=$level, input=$input")
      }
      val isInitiallyExpanded = when (currentLine[currentLineLevel]) {
        '-' -> false
        '+' -> true
        else -> null
      }
      val text = currentLine.substring(currentLineLevel + 1)
      val userObject = UserObject(project, Data(text, isInitiallyExpanded))
      ++line
      ++level
      userObject.children = parseChildren()
      --level
      result += userObject 
    }
    return result
  }
}

private class BackendTreeModel(
  private val root: UserObject,
  private val coroutineScope: CoroutineScope,
) : BaseTreeModel<UserObject>(), AsyncTreeModel.AsyncChildrenProvider<UserObject>, InvokerSupplier {

  private val invoker = Invoker.forBackgroundThreadWithoutReadAction(coroutineScope.asDisposable())

  override fun getInvoker(): Invoker = invoker

  override fun getRoot(): Any = root

  override fun getChildren(parent: Any?): List<UserObject>? = (parent as? UserObject?)?.children

  override fun getChildrenAsync(parent: Any?): Promise<out List<UserObject>?> {
    // This part is important: to check that TreeState actually waits until every node is loaded,
    // we need to imitate slow async children retrieval.
    // If we imitate slow getChildren, then the invoker will be blocked,
    // and it'll cause TreeState to wait even if there are bugs in TreeState logic.
    // (Its visitor will be waiting for the nodes to load even if it doesn't visit those nodes.)
    val promise = AsyncPromise<List<UserObject>?>()
    coroutineScope.launch {
      delay(10.milliseconds)
      invoker.invokeLater { 
        promise.setResult(getChildren(parent))
      }
    }
    return promise
  }
}

private data class Data(
  val text: String,
  val isInitiallyExpanded: Boolean?,
)

private class UserObject(project: Project, value: Data) : AbstractTreeNode<Data>(project, value) {
  var children: List<UserObject>? = null

  override fun getChildren(): @Unmodifiable Collection<UserObject> = children ?: emptyList()

  override fun isAlwaysShowPlus(): Boolean = children != null

  override fun isAlwaysLeaf(): Boolean = children == null

  override fun update(presentation: PresentationData) {
    presentation.presentableText = value.text
  }

  override fun toString(): @NlsSafe String = value.text
}
