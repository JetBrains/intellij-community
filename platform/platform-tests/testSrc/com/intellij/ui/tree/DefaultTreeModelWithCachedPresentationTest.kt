// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.ide.util.treeView.CachedIconPresentation
import com.intellij.ide.util.treeView.CachedPresentationData
import com.intellij.ide.util.treeView.CachedTreePathElement
import com.intellij.ide.util.treeView.CachedTreePresentationData
import com.intellij.ide.util.treeView.CachedTreePresentationNode
import com.intellij.ide.util.treeView.CachedTreePresentationSupport
import com.intellij.ide.util.treeView.DefaultTreeModelWithCachedPresentation
import com.intellij.ide.util.treeView.PathElementIdProvider
import com.intellij.ide.util.treeView.TreeState
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.util.ui.tree.TreeUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

internal class DefaultTreeModelWithCachedPresentationTest {

  private lateinit var sut: DefaultTreeModelWithCachedPresentation
  private lateinit var cps: CachedTreePresentationSupport
  private lateinit var mirrorModel: DefaultTreeModel
  private var eventCount: Int = 0

  @BeforeEach
  fun setUp() {
    sut = DefaultTreeModelWithCachedPresentation()
    cps = sut as CachedTreePresentationSupport
    mirrorModel = DefaultTreeModel(null)
    sut.addTreeModelListener(MirroringTreeModelListener())
    eventCount = 0
  }

  @Test
  fun `restore null presentation`() {
    cps.cachedPresentation = null
    assertThat(cps.cachedPresentation).isNull()
    assertThat(sut.root).isNull()
    assertThat(eventCount).isZero()
  }

  @Test
  fun `restore cached root presentation`() = restorePresentationTest(
    initialState = "",
    cachedPresentation = "root",
    expectedResult = "*root",
    expectedEventCount = 1, // root changed
  )

  @Test
  fun `restore cached root presentation with already existing root`() = restorePresentationTest(
    initialState = "root",
    cachedPresentation = "root",
    expectedResult = "root",
    expectedEventCount = 1, // root changed
    expectedLoadedNodes = 0,
  )

  @Test
  fun `restore cached root presentation with already existing root and cached children`() = restorePresentationTest(
    initialState = "root",
    cachedPresentation = """
      |root
      | child1
      | child2
      """.trimMargin(),
    expectedResult = """
      |root
      | *child1
      | *child2
      """.trimMargin(),
    expectedEventCount = 2, // root changed, children inserted
  )

  @Test
  fun `restore cached root presentation with already existing root and cached children, then load children`() = restorePresentationTest(
    initialState = "root",
    cachedPresentation = """
      |root
      | child1
      | child2
      """.trimMargin(),
    loadChildren = listOf(
      "root" to listOf("child1", "child2"),
    ),
    expectedResult = """
      |root
      | child1
      | child2
      """.trimMargin(),
    expectedEventCount = 4, // root set, presentation set (root with everything), cached children removed, actual children added
    expectedLoadedNodes = 2,
  )

  @Test
  fun `restore cached presentation with already existing root and some children`() = restorePresentationTest(
    initialState = """
      |root
      | child1
      | child2
      """.trimMargin(),
    cachedPresentation = """
      |root
      | child1
      |  child11
      |  child12
      | child2
      """.trimMargin(),
    expectedResult = """
      |root
      | child1
      |  *child11
      |  *child12
      | child2
      """.trimMargin(),
    expectedEventCount = 2, // root set, presentation set
  )

  @Test
  fun `restore cached root presentation with cached children`() = restorePresentationTest(
    initialState = "",
    cachedPresentation = """
      |root
      | child1
      | child2
      """.trimMargin(),
    expectedResult = """
      |*root
      | *child1
      | *child2
      """.trimMargin(),
    expectedEventCount = 1, // root set with all children
  )

  @Test
  fun `restore presentation with cached children, then load root`() = restorePresentationTest(
    initialState = "",
    cachedPresentation = """
      |root
      | child1
      | child2
      """.trimMargin(),
    loadRoot = "root",
    expectedResult = """
      |root
      | *child1
      | *child2
      """.trimMargin(),
    expectedEventCount = 2, // root set with all children, root loaded
  )

  @Test
  fun `restore presentation with cached multiple level children`() = restorePresentationTest(
    initialState = "",
    cachedPresentation = """
      |root
      | child1
      |  child11
      |  child12
      | child2
      """.trimMargin(),
    expectedResult = """
      |*root
      | *child1
      |  *child11
      |  *child12
      | *child2
      """.trimMargin(),
    expectedEventCount = 1, // root set with all children
  )

  @Test
  fun `restore presentation with cached multiple level children, then load root`() = restorePresentationTest(
    initialState = "",
    cachedPresentation = """
      |root
      | child1
      |  child11
      |  child12
      | child2
      """.trimMargin(),
    loadRoot = "root",
    expectedResult = """
      |root
      | *child1
      |  *child11
      |  *child12
      | *child2
      """.trimMargin(),
    expectedEventCount = 2, // root set with all children, root reload
  )

  @Test
  fun `restore presentation with cached multiple level children, then load root and one level children`() = restorePresentationTest(
    initialState = "",
    cachedPresentation = """
      |root
      | child1
      |  child11
      |  child12
      | child2
      """.trimMargin(),
    loadRoot = "root",
    loadChildren = listOf(
      "root" to listOf("child1", "child2"),
    ),
    expectedResult = """
      |root
      | child1
      |  *child11
      |  *child12
      | child2
      """.trimMargin(),
    expectedEventCount = 4, // root set with all children, root reload, children removed, children added
  )

  @Test
  fun `restore presentation, then load root that doesn't match`() = restorePresentationTest(
    initialState = "",
    cachedPresentation = """
      |root
      | child1
      | child2
      """.trimMargin(),
    loadRoot = "another_root",
    expectedResult = """
      |another_root
      """.trimMargin(),
    expectedEventCount = 2, // root set with all children, root reload
    expectedLoadedNodes = 1,
  )

  @Test
  fun `restore presentation, then load matching root and then children that doesn't match`() = restorePresentationTest(
    initialState = "",
    cachedPresentation = """
      |root
      | child1
      |  child11
      | child2
      """.trimMargin(),
    loadRoot = "root",
    loadChildren = listOf(
      "root" to listOf("another_child1", "child2"),
    ),
    expectedResult = """
      |root
      | another_child1
      | child2
      """.trimMargin(),
    expectedEventCount = 4, // root set with all children, root reload, children remove, children add
    expectedLoadedNodes = 3,
  )

  @Test
  fun `restore presentation, then load matching root and children, then insert more children`() = restorePresentationTest(
    initialState = "",
    cachedPresentation = """
      |root
      | child1
      | child2
      """.trimMargin(),
    loadRoot = "root",
    loadChildren = listOf(
      "root" to listOf("child1", "child2"),
    ),
    insertRemoveChildren = listOf(
      "root" to listOf(2 to "child3"),
    ),
    expectedResult = """
      |root
      | child1
      | child2
      | child3
      """.trimMargin(),
    expectedEventCount = 5, // root set with all children, root reload, children remove, children add, child insert
    expectedLoadedNodes = 3,
  )

  @Test
  fun `restore presentation, then load matching root and empty children`() = restorePresentationTest(
    initialState = "",
    cachedPresentation = """
      |root
      | child1
      | child2
      """.trimMargin(),
    loadRoot = "root",
    loadChildren = listOf(
      "root" to emptyList(),
    ),
    expectedResult = """
      |root
      """.trimMargin(),
    expectedEventCount = 3, // root set with all children, root reload, children removed
    expectedLoadedNodes = 1,
  )

  @Test
  fun `restore presentation, then load matching root and children, then remove a child`() = restorePresentationTest(
    initialState = "",
    cachedPresentation = """
      |root
      | child1
      | child2
      """.trimMargin(),
    loadRoot = "root",
    loadChildren = listOf(
      "root" to listOf("child1", "child2"),
    ),
    insertRemoveChildren = listOf(
      "root" to listOf(0),
    ),
    expectedResult = """
      |root
      | child2
      """.trimMargin(),
    expectedEventCount = 5, // root set with all children, root reload, children removed, children loaded, child removed
    expectedLoadedNodes = 3,
  )

  @Test
  fun `restore presentation, then load matching root and children, then update root`() = restorePresentationTest(
    initialState = "",
    cachedPresentation = """
      |root
      | child1
      | child2
      """.trimMargin(),
    loadRoot = "root",
    loadChildren = listOf(
      "root" to listOf("child1", "child2"),
    ),
    updateNodes = listOf(
      null to listOf("root" to "new_root"),
    ),
    expectedResult = """
      |new_root
      | child1
      | child2
      """.trimMargin(),
    expectedEventCount = 5, // root set with all children, root reload, children removed, children loaded, root updated
    expectedLoadedNodes = 3,
  )

  @Test
  fun `update children - empty`() = updateChildrenTest(
    initialState = "root",
    updateChildren = listOf(
      "root" to emptyList(),
    ),
    expectSame = emptyList(),
    expectedResult = "root",
    expectedEventCount = 1, // initial state
  )

  @Test
  fun `update children - empty to not empty`() = updateChildrenTest(
    initialState = "root",
    updateChildren = listOf(
      "root" to listOf("child1"),
    ),
    expectSame = emptyList(),
    expectedResult = """
      |root
      | child1
      """.trimMargin(),
    expectedEventCount = 2, // initial state + inserted
  )

  @Test
  fun `update children - not empty to empty`() = updateChildrenTest(
    initialState = """
      |root
      | child1
      | child2
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf(),
    ),
    expectSame = emptyList(),
    expectedResult = """
      |root
      """.trimMargin(),
    expectedEventCount = 2, // initial state + removed
  )

  @Test
  fun `update children - to single identical`() = updateChildrenTest(
    initialState = """
      |root
      | child1
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf("child1"),
    ),
    expectSame = listOf("root/child1"),
    expectedResult = """
      |root
      | child1
      """.trimMargin(),
    expectedEventCount = 2, // initial state + changed
  )

  @Test
  fun `update children - insert after`() = updateChildrenTest(
    initialState = """
      |root
      | child1
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf("child1", "child2"),
    ),
    expectSame = listOf("root/child1"),
    expectedResult = """
      |root
      | child1
      | child2
      """.trimMargin(),
    expectedEventCount = 3, // initial state + inserted + changed
  )

  @Test
  fun `update children - insert before`() = updateChildrenTest(
    initialState = """
      |root
      | child1
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf("child0", "child1"),
    ),
    expectSame = listOf("root/child1"),
    expectedResult = """
      |root
      | child0
      | child1
      """.trimMargin(),
    expectedEventCount = 3, // initial state + inserted + changed
  )

  @Test
  fun `update children - insert around`() = updateChildrenTest(
    initialState = """
      |root
      | child1
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf("child0", "child1", "child2"),
    ),
    expectSame = listOf("root/child1"),
    expectedResult = """
      |root
      | child0
      | child1
      | child2
      """.trimMargin(),
    expectedEventCount = 3, // initial state + inserted + changed
  )

  @Test
  fun `update children - complicated insert`() = updateChildrenTest(
    initialState = """
      |root
      | child1
      | child2
      | child3
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf("child0.5", "child1", "child1.1", "child1.2", "child2", "child3", "child4"),
    ),
    expectSame = listOf("root/child1", "root/child2", "root/child3"),
    expectedResult = """
      |root
      | child0.5
      | child1
      | child1.1
      | child1.2
      | child2
      | child3
      | child4
      """.trimMargin(),
    expectedEventCount = 3, // initial state + inserted + changed
  )

  @Test
  fun `update children - remove the only`() = updateChildrenTest(
    initialState = """
      |root
      | child1
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf(),
    ),
    expectSame = emptyList(),
    expectedResult = """
      |root
      """.trimMargin(),
    expectedEventCount = 2, // initial state + removed
  )

  @Test
  fun `update children - remove many`() = updateChildrenTest(
    initialState = """
      |root
      | child1
      | child2
      | child3
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf(),
    ),
    expectSame = emptyList(),
    expectedResult = """
      |root
      """.trimMargin(),
    expectedEventCount = 2, // initial state + removed
  )

  @Test
  fun `update children - remove one`() = updateChildrenTest(
    initialState = """
      |root
      | child1
      | child2
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf("child1"),
    ),
    expectSame = listOf("root/child1"),
    expectedResult = """
      |root
      | child1
      """.trimMargin(),
    expectedEventCount = 3, // initial state + removed + changed
  )

  @Test
  fun `update children - remove complicated`() = updateChildrenTest(
    initialState = """
      |root
      | child1
      | child1.5
      | child2
      | child3
      | child3.5
      | child4
      | child5
      | child6
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf("child2", "child4"),
    ),
    expectSame = listOf("root/child2", "root/child4"),
    expectedResult = """
      |root
      | child2
      | child4
      """.trimMargin(),
    expectedEventCount = 3, // initial state + removed + changed
  )

  @Test
  fun `update children - to single equal-but-not-identical`() = updateChildrenTest(
    initialState = """
      |root
      | child1
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf("CHILD1"),
    ),
    expectSame = listOf("root/child1"),
    expectedResult = """
      |root
      | CHILD1
      """.trimMargin(),
    expectedEventCount = 2, // initial state + changed
  )

  @Test
  fun `update children - a generic do-it-all test`() = updateChildrenTest(
    initialState = """
      |root
      | child1
      | child2
      | child3
      | child4
      | child5
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf("child0.5", "CHILD1", "child1.1", "child1.2", "child2.5", "child3", "child3.5", "child4", "child5", "child6", "child7"),
    ),
    expectSame = listOf("root/child1", "root/child3", "root/child4", "root/child5"),
    expectedResult = """
      |root
      | child0.5
      | CHILD1
      | child1.1
      | child1.2
      | child2.5
      | child3
      | child3.5
      | child4
      | child5
      | child6
      | child7
      """.trimMargin(),
    expectedEventCount = 4, // initial state + removed + inserted + changed
  )

  @Test
  fun `update children - a single update with a cached presentation`() = updateChildrenTest(
    initialState = """
      |root
      | child1
      | child2
      | child3
      | child4
      | child5
      """.trimMargin(),
    cachedPresentation = """
      |root
      | child1
      |  child1-1
      | child2
      |  child2-1
      |  child2-2
      | child3
      | child4
      |  child4-1
      |  child4-2
      |  child4-3
      | child5
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf("child0.5", "CHILD1", "child1.1", "child1.2", "child2.5", "child3", "child3.5", "child4", "child5", "child6", "child7"),
    ),
    expectSame = listOf("root/child1", "root/child3", "root/child4", "root/child5"),
    expectedResult = """
      |root
      | child0.5
      | CHILD1
      |  *child1-1
      | child1.1
      | child1.2
      | child2.5
      | child3
      | child3.5
      | child4
      |  *child4-1
      |  *child4-2
      |  *child4-3
      | child5
      | child6
      | child7
      """.trimMargin(),
    expectedEventCount = 5, // initial state + cached + removed + inserted + changed
    expectedLoadedNodes = null, // because not all of them are loaded in this test
  )

  @Test
  fun `update children - two level updates with a cached presentation`() = updateChildrenTest(
    initialState = """
      |root
      """.trimMargin(),
    cachedPresentation = """
      |root
      | child1
      |  child1-1
      | child2
      |  child2-1
      |  child2-2
      | child3
      |  child3-1
      | child4
      |  child4-1
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf("child1", "child2", "child3", "child5"),
      "root/child1" to listOf("child1-1"),
      "root/child2" to listOf("child2-3"),
      "root/child5" to listOf("child5-1"),
    ),
    expectSame = listOf("root"),
    expectedResult = """
      |root
      | child1
      |  child1-1
      | child2
      |  child2-3
      | child3
      |  *child3-1
      | child5
      |  child5-1
      """.trimMargin(),
    expectedEventCount = 9, // initial state + cached + removed 1-4 + inserted 1-5 + (2x) remove-insert 1&2 children + insert 5-1
    expectedLoadedNodes = null, // because not all of them are loaded in this test
  )

  @Test
  fun `update children - two level updates with a cached presentation and its full replacement`() = updateChildrenTest(
    initialState = """
      |root
      """.trimMargin(),
    cachedPresentation = """
      |root
      | child1
      |  child1-1
      | child2
      |  child2-1
      |  child2-2
      | child3
      |  child3-1
      | child4
      |  child4-1
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf("child1", "child2", "child3", "child5"),
      "root/child1" to listOf("child1-1"),
      "root/child2" to listOf("child2-3"),
      "root/child3" to listOf("child3-1", "child3-2"),
      "root/child5" to listOf("child5-1", "child5-2"),
    ),
    expectSame = listOf("root"),
    expectedResult = """
      |root
      | child1
      |  child1-1
      | child2
      |  child2-3
      | child3
      |  child3-1
      |  child3-2
      | child5
      |  child5-1
      |  child5-2
      """.trimMargin(),
    expectedEventCount = 11, // initial state + cached + removed 1-4 + inserted 1-5 + (3x) remove-insert 1&2&3 children + insert 5 children
    expectedLoadedNodes = 8, // 10, but the last two children (5-1, 5-2) are inserted after the cached presentation is already gone
  )

  @Test
  fun `update children - a one-to-one correspondence with the cached presentation`() = updateChildrenTest(
    initialState = """
      |root
      """.trimMargin(),
    cachedPresentation = """
      |root
      | child1
      |  child1-1
      | child2
      |  child2-1
      """.trimMargin(),
    updateChildren = listOf(
      "root" to listOf("child1", "child2"),
      "root/child1" to listOf("child1-1"),
      "root/child2" to listOf("child2-1"),
    ),
    expectSame = listOf("root"),
    expectedResult = """
      |root
      | child1
      |  child1-1
      | child2
      |  child2-1
      """.trimMargin(),
    expectedEventCount = 8, // initial state + cached + removed 1-2 + inserted 1-2 + (2x) remove-insert 1&2 children
    expectedLoadedNodes = 4, // except root
  )

  @Suppress("UnnecessaryVariable") // Variables are used to make the code self-documented!
  private fun restorePresentationTest(
    initialState: String,
    cachedPresentation: String,
    loadRoot: String? = null,
    loadChildren: List<Pair<String, List<String>>> = emptyList(),
    insertRemoveChildren: List<Pair<String, List<Any>>> = emptyList(),
    updateNodes: List<Pair<String?, List<Pair<String, String>>>> = emptyList(),
    expectedResult: String,
    expectedEventCount: Int,
    expectedLoadedNodes: Int? = null,
  ) {
    sut.setRoot(parse(initialState))
    // This checks that the listeners are working as expected:
    assertThat(dump(mirrorModel)).`as`("initial mirror").isEqualTo(initialState)
    val cp = parse(cachedPresentation)?.toCachedPresentation()?.createTree()
    if (cp != null) {
      cps.applyAlreadyLoadedNodesTo(cp)
    }
    cps.cachedPresentation = cp
    var loadedNodeCount: Int? = null
    sut.promiseRealNodes().onProcessed { paths ->
      loadedNodeCount = paths?.size
    }
    if (loadRoot != null) {
      sut.setRoot(DefaultMutableTreeNode(DefaultTreeModelUserObject(loadRoot)))
    }
    for ((parentPathString, childStrings) in loadChildren) {
      val parentPath = path(parentPathString)
      val children = childStrings.map { DefaultMutableTreeNode(DefaultTreeModelUserObject(it)) }
      sut.setChildren(parentPath.lastPathComponent as DefaultMutableTreeNode, children)
    }
    for ((parentPathString, childActions) in insertRemoveChildren) {
      val parentPath = path(parentPathString)
      for (childAction in childActions) {
        when (childAction) {
          is Int -> {
            val index = childAction
            sut.removeChild(parentPath.lastPathComponent as DefaultMutableTreeNode, index)
          }
          is Pair<*, *> -> {
            val index = childAction.first as Int
            val child = childAction.second as String
            val newChild = DefaultMutableTreeNode(DefaultTreeModelUserObject(child))
            sut.insertChild(parentPath.lastPathComponent as DefaultMutableTreeNode, index, newChild)
          }
          else -> throw IllegalArgumentException("An Int means remove, a Pair<Int, String> means insert, but we have $childAction")
        }
      }
    }
    for ((parent, updates) in updateNodes) {
      if (parent == null) {
        val update = updates.single()
        sut.updateNode(path(update.first).lastPathComponent as DefaultMutableTreeNode, DefaultTreeModelUserObject(update.second))
      }
    }
    assertThat(dump(sut)).`as`("result model").isEqualTo(expectedResult)
    // This checks that the listeners are working as expected:
    assertThat(dump(mirrorModel)).`as`("result mirror").isEqualTo(expectedResult)
    assertThat(eventCount).`as`("event count").isEqualTo(expectedEventCount)
    assertThat(loadedNodeCount).`as`("loaded nodes").isEqualTo(expectedLoadedNodes)
  }

  private fun updateChildrenTest(
    initialState: String,
    cachedPresentation: String? = null,
    updateChildren: List<Pair<String, List<String>>> = emptyList(),
    expectSame: List<String>,
    expectedResult: String,
    expectedEventCount: Int,
    expectedLoadedNodes: Int? = 0, // the most common case for this function, as most tests don't use a cached presentation
  ) {
    sut.setRoot(parse(initialState))
    // This checks that the listeners are working as expected:
    assertThat(dump(mirrorModel)).`as`("initial mirror").isEqualTo(initialState)
    if (cachedPresentation != null) {
      val cp = parse(cachedPresentation)?.toCachedPresentation()?.createTree()
      if (cp != null) {
        cps.applyAlreadyLoadedNodesTo(cp)
      }
      cps.cachedPresentation = cp
    }
    var loadedNodeCount: Int? = null
    sut.promiseRealNodes().onProcessed { paths ->
      loadedNodeCount = paths?.size
    }
    val pathsThatWillBeTheSame = expectSame.map { path(it) }
    for ((parentPathString, childStrings) in updateChildren) {
      val parentPath = path(parentPathString)
      val parent = parentPath.lastPathComponent
      // We use case-insensitive mapping to mimic updating of existing children.
      // So ["child1"] -> ["child2"] = removed + inserted, but ["child1"] -> ["CHILD1"] = updated.
      val existingChildrenByText = (0 until sut.getChildCount(parent))
        .asSequence()
        .map { sut.getChild(parent, it) }
        .map { it.myUserObject?.text?.lowercase() to it }
        .filter { it.first != null }
        .toMap()
      val newChildren = childStrings.map { DefaultMutableTreeNode(DefaultTreeModelUserObject(it)) }
      sut.updateChildren(parent as DefaultMutableTreeNode, newChildren) { newChild ->
        val myUserObject = newChild.myUserObject
        // By the contract this thing is never called for cached nodes, so must never be null.
        assertThat(myUserObject).isNotNull()
        existingChildrenByText[myUserObject?.text?.lowercase()]
      }
    }
    // Check that the paths that are supposed to be unaffected retained their node identity
    // (and therefore anything that might be mapped to them anywhere).
    // The user objects might have been updated, though.
    val theSamePaths = expectSame.map { path(it) }
    assertThat(theSamePaths).hasSameSizeAs(pathsThatWillBeTheSame)
    for ((pathWas, pathIs) in pathsThatWillBeTheSame.zip(theSamePaths)) {
      assertThat(pathIs.lastPathComponent)
        .`as`(pathIs.toString())
        .isSameAs(pathWas.lastPathComponent)
    }
    assertThat(dump(sut)).`as`("result model").isEqualTo(expectedResult)
    // This checks that the listeners are working as expected:
    assertThat(dump(mirrorModel)).`as`("result mirror").isEqualTo(expectedResult)
    assertThat(eventCount).`as`("event count").isEqualTo(expectedEventCount)
    assertThat(loadedNodeCount).`as`("loaded nodes").isEqualTo(expectedLoadedNodes)
  }

  @Test
  fun `helper test - empty`() = parserTest("")

  @Test
  fun `helper test - single root`() = parserTest("root")

  @Test
  fun `helper test - root with children`() = parserTest(
    """
      |root
      | child1
      | child2
    """.trimMargin()
  )

  @Test
  fun `helper test - cached root with children`() = parserTest(
    """
      |*root
      | *child1
      | *child2
    """.trimMargin()
  )

  @Test
  fun `helper test - root with cached children`() = parserTest(
    """
      |root
      | *child1
      | *child2
    """.trimMargin()
  )

  private fun parserTest(input: String) {
    val root = parse(input)
    sut.setRoot(root)
    assertThat(dump(sut)).isEqualTo(input)
  }

  @Test
  fun `helper test - convert to a cached presentation`() {
    val presentation = parse("""
      |root
      | child1
    """.trimMargin())?.toCachedPresentation()
    assertThat(presentation?.pathElement?.id).isEqualTo("root")
    assertThat(presentation?.presentation?.text).isEqualTo("root")
    val child1 = presentation?.children[0]
    assertThat(child1?.pathElement?.id).isEqualTo("child1")
    assertThat(child1?.presentation?.text).isEqualTo("child1")
  }

  private fun path(pathString: String): TreePath {
    val elements = pathString.split('/')
    val root = sut.root as DefaultMutableTreeNode
    if (!pathElementMatches(elements[0], root)) throw IllegalArgumentException("path=$pathString, root=$root")
    return path(CachingTreePath(root), elements, 1)
  }

  private fun path(parentPath: TreePath, elements: List<String>, index: Int): TreePath {
    if (index == elements.size) return parentPath
    val parent = parentPath.lastPathComponent as DefaultMutableTreeNode
    val pathElement = elements[index]
    val child = (0 until parent.childCount)
      .map { parent.getChildAt(it) as DefaultMutableTreeNode }
      .single { pathElementMatches(pathElement, it) }
    return path(parentPath.pathByAddingChild(child), elements, index + 1)
  }

  private fun pathElementMatches(element: String, node: DefaultMutableTreeNode): Boolean {
    val userObject = node.userObject
    return when (userObject) {
      is DefaultTreeModelUserObject -> userObject.text.equals(element, ignoreCase = true)
      is CachedTreePresentationNode -> userObject.data.presentation.text.equals(element, ignoreCase = true)
      else -> false
    }
  }

  private fun parse(input: String): DefaultMutableTreeNode? = DefaultMutableTreeModelParser(input).parse()

  private fun dump(treeModel: TreeModel): String {
    val result = StringBuilder()
    val root = treeModel.root as? DefaultMutableTreeNode?
    if (root != null) {
      dump(root, 0, result)
    }
    return result.toString().trim()
  }

  private fun dump(node: DefaultMutableTreeNode, depth: Int, result: StringBuilder) {
    result.append(" ".repeat(depth))
    val userObject = node.userObject
    when (userObject) {
        is DefaultTreeModelUserObject -> {
          if (userObject.isCached) {
            result.append("*")
          }
          result.append(userObject.text)
        }
      is CachedTreePresentationNode -> {
        result.append("*")
        result.append(userObject.data.presentation.text)
      }
      else -> throw IllegalArgumentException("Unknown user object type: $userObject")
    }
    result.append("\n")
    for (i in 0 until node.childCount) {
      val child = node.getChildAt(i) as DefaultMutableTreeNode
      dump(child, depth + 1, result)
    }
  }

  private fun DefaultMutableTreeNode.toCachedPresentation(): CachedTreePresentationData {
    val userObject = checkNotNull(myUserObject) { "Can only convert a real user object to a cached one, but got ${this.userObject}" }
    return CachedTreePresentationData(
      pathElement = DefaultCachedPathElement(userObject),
      presentation = DefaultCachedPresentationData(userObject.text),
      extraAttributes = null,
      children = (0 until this.childCount).map { (getChildAt(it) as DefaultMutableTreeNode).toCachedPresentation() },
    )
  }

  private inner class MirroringTreeModelListener : TreeModelListener {
    override fun treeNodesChanged(e: TreeModelEvent) {
      ++eventCount
      val childIndices = e.childIndices
      if (childIndices == null) {
        assertThat(e.children).isNull()
        (mirrorModel.root as DefaultMutableTreeNode).userObject = (e.treePath.lastPathComponent as DefaultMutableTreeNode).userObject
        mirrorModel.nodeChanged(mirrorModel.root as DefaultMutableTreeNode)
      }
      else {
        assertThat(e.children).isNotNull()
        assertThat(e.children).hasSameSizeAs(childIndices)
        val parent = findMatchingNode(mirrorModel, e.treePath)
        for ((i, child) in childIndices zip e.children) {
          val mirrorChild = mirrorModel.getChild(parent, i) as DefaultMutableTreeNode
          mirrorChild.userObject = (child as DefaultMutableTreeNode).userObject
        }
        mirrorModel.nodesChanged(parent, childIndices)
      }
    }

    override fun treeNodesInserted(e: TreeModelEvent) {
      ++eventCount
      val parent = findMatchingNode(mirrorModel, e.treePath)
      assertThat(e.children).isNotNull()
      assertThat(e.children).hasSameSizeAs(e.childIndices)
      for ((i, child) in e.childIndices zip e.children) {
        mirrorModel.insertNodeInto(deepCopy(child as DefaultMutableTreeNode), parent, i)
      }
    }

    override fun treeNodesRemoved(e: TreeModelEvent) {
      ++eventCount
      val parent = findMatchingNode(mirrorModel, e.treePath)
      assertThat(e.children).isNotNull()
      assertThat(e.children).hasSameSizeAs(e.childIndices)
      for (i in e.childIndices.reversed()) {
        mirrorModel.removeNodeFromParent(mirrorModel.getChild(parent, i) as DefaultMutableTreeNode)
      }
    }

    override fun treeStructureChanged(e: TreeModelEvent) {
      ++eventCount
      val changedPath = e.treePath
      if (changedPath == null) {
        mirrorModel.setRoot(null)
      }
      else {
        if (changedPath.parentPath != null) throw IllegalStateException("We don't change structure of non-root nodes here, but got $e")
        mirrorModel.setRoot(deepCopy(changedPath.lastPathComponent as DefaultMutableTreeNode))
      }
    }
  }

  private fun findMatchingNode(model: DefaultTreeModel, path: TreePath): DefaultMutableTreeNode {
    return findMatchingPath(model, path).lastPathComponent as DefaultMutableTreeNode
  }

  private fun findMatchingPath(model: DefaultTreeModel, path: TreePath): TreePath {
    val lookupParentPath = path.parentPath
    if (lookupParentPath == null) {
      val ourUserObject = (model.root as DefaultMutableTreeNode).userObject
      val lookupUserObject = (path.lastPathComponent as DefaultMutableTreeNode).userObject
      if (ourUserObject != lookupUserObject) throw IllegalArgumentException("Path not found: root = ${model.root}, path=$path")
      return CachingTreePath(model.root)
    }
    else {
      val ourParentPath = findMatchingPath(model, lookupParentPath)
      val childCount = model.getChildCount(ourParentPath.lastPathComponent)
      val lookupUserObject = (path.lastPathComponent as DefaultMutableTreeNode).userObject
      for (i in 0 until childCount) {
        val child = model.getChild(ourParentPath.lastPathComponent, i)
        val ourUserObject = (child as DefaultMutableTreeNode).userObject
        if (ourUserObject == lookupUserObject) return ourParentPath.pathByAddingChild(child)
      }
      throw IllegalArgumentException("Path not found: found parent = $ourParentPath, can't find $lookupUserObject")
    }
  }

  private fun deepCopy(node: DefaultMutableTreeNode): DefaultMutableTreeNode {
    val result = DefaultMutableTreeNode(node.userObject)
    for (i in 0 until node.childCount) {
      result.insert(deepCopy(node.getChildAt(i) as DefaultMutableTreeNode), i)
    }
    return result
  }
}

private val DefaultMutableTreeNode.myUserObject: DefaultTreeModelUserObject? get() = userObject as? DefaultTreeModelUserObject

private data class DefaultTreeModelUserObject(val text: String, val isCached: Boolean) : PathElementIdProvider {
  constructor(text: String) : this(text.removePrefix("*"), isCached = text.startsWith("*"))

  override fun getPathElementId(): String = text

  override fun getPathElementType(): String = TreeState.defaultPathElementType(this)
}

private data class DefaultCachedPathElement(override val type: String, override val id: String) : CachedTreePathElement {
  constructor(userObject: DefaultTreeModelUserObject) : this(userObject.pathElementType, userObject.pathElementId)

  override fun matches(node: Any): Boolean {
    val userObject = TreeUtil.getUserObject(node)
    if (userObject !is PathElementIdProvider) return false
    return userObject.pathElementType == type && userObject.pathElementId == id
  }
}

private data class DefaultCachedPresentationData(override val text: String) : CachedPresentationData {
  override val iconData: CachedIconPresentation?
    get() = null

  override val isLeaf: Boolean
    get() = false
}

private class DefaultMutableTreeModelParser(private val input: String) {
  private val lines = input.split("\n")
  private var line = 0
  private var depth = 0

  fun parse(): DefaultMutableTreeNode? {
    return parseChildren().singleOrNull()
  }

  private fun parseChildren(): List<DefaultMutableTreeNode> {
    val result = mutableListOf<DefaultMutableTreeNode>()
    while (line < lines.size) {
      val lineString = lines[line]
      val lineDepth = lineString.indexOfFirst { !it.isWhitespace() }
      val lineText = lineString.trim()
      if (lineDepth < depth) break // go back up
      if (lineDepth > depth) {
        throw IllegalArgumentException("Depth should not increase this much: depth=$depth, lineDepth=$lineDepth, input=$input")
      }
      val userObject = DefaultTreeModelUserObject(lineText)
      val node = DefaultMutableTreeNode(userObject)
      ++line

      ++depth
      val children = parseChildren()
      for ((index, child) in children.withIndex()) {
        node.insert(child, index)
      }
      --depth

      result += node
    }
    return result
  }
}
