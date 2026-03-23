// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.ide.bookmark.BookmarksListProvider
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.impl.breakpoints.BreakpointListUpdaterService
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import org.assertj.core.api.Assertions
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for breakpoint tree structure in the Bookmarks tool window (IJPL-237810).
 */
@RunWith(JUnit4::class)
class BreakpointListProviderTest : XBreakpointsTestCase() {

  private class MyFieldBreakpointType :
    XBreakpointType<XBreakpoint<MyBreakpointProperties>, MyBreakpointProperties>("testField", "Field Watchpoints") {
    override fun getDisplayText(breakpoint: XBreakpoint<MyBreakpointProperties>): String = breakpoint.properties?.myOption ?: ""
    override fun createProperties(): MyBreakpointProperties = MyBreakpointProperties()
  }

  private val myFieldBreakpointType = MyFieldBreakpointType()

  override fun setUp() {
    super.setUp()
    Registry.get("ide.bookmark.show.all.breakpoints").setValue(true, testRootDisposable)
    XBreakpointType.EXTENSION_POINT_NAME.point.registerExtension(myFieldBreakpointType, testRootDisposable)
    // Force lazy init of RootNode and its collection coroutine
    findProvider().createNode()
  }

  private fun addFieldBreakpoint(
    breakpointManager: XBreakpointManagerImpl,
    properties: MyBreakpointProperties,
  ): XBreakpoint<MyBreakpointProperties> =
    breakpointManager.addBreakpoint(myFieldBreakpointType, properties)

  private fun findProvider(): BookmarksListProvider =
    BookmarksListProvider.EP.getExtensions(myProject).first { it.javaClass.name.contains("BreakpointListProvider") }

  private fun getNodeText(node: AbstractTreeNode<*>): String {
    node.update()
    return node.presentation.presentableText ?: "?"
  }

  /**
   * Returns true if the subtree rooted at [node] contains at least one non-default breakpoint item.
   * Default breakpoints (registered by platform plugins) are filtered out so tests only see breakpoints they create.
   */
  private fun hasTestBreakpoints(node: AbstractTreeNode<*>): Boolean {
    val obj = node.equalityObject
    if (obj is BreakpointItem) return !obj.isDefaultBreakpoint
    return node.children.any { hasTestBreakpoints(it) }
  }

  private fun dumpTree(node: AbstractTreeNode<*>, indent: Int = 0): String {
    val text = getNodeText(node)
    val children = node.children.filter { hasTestBreakpoints(it) }
    val line = "${"  ".repeat(indent)}$text"
    if (children.isEmpty()) return line
    return "$line\n${children.joinToString("\n") { dumpTree(it, indent + 1) }}"
  }

  private fun assertTree(expected: String) {
    val provider = findProvider()
    val exp = expected.trimIndent().trimEnd()
    awaitUpdate()
    val node = provider.createNode()
    val lastDump = if (node != null) dumpTree(node).trimEnd() else "<null>"
    Assertions.assertThat(lastDump).isEqualTo(exp)
  }

  fun awaitUpdate() {
    val service = project.service<BreakpointListUpdaterService>()
    runBlockingMaybeCancellable {
      service.updateJob?.join()
    }
  }

  @Test
  fun testGroupedByType() {
    addLineBreakpoint(myBreakpointManager, "file:///a.java", 1, MyBreakpointProperties("a"))
    addLineBreakpoint(myBreakpointManager, "file:///b.java", 2, MyBreakpointProperties("b"))

    assertTree("""
      Breakpoints
        239
          b.java:3
          a.java:2
    """)
  }

  @Test
  fun testCustomGroupNesting() {
    val bp = addLineBreakpoint(myBreakpointManager, "file:///a.java", 1, MyBreakpointProperties("a"))
    (bp as XBreakpointBase<*, *, *>).group = "MyGroup"

    assertTree("""
      Breakpoints
        MyGroup
          239
            a.java:2
    """)
  }

  @Test
  fun testGroupedAndUngrouped() {
    val bp1 = addLineBreakpoint(myBreakpointManager, "file:///in.java", 1, MyBreakpointProperties("in"))
    addLineBreakpoint(myBreakpointManager, "file:///out.java", 2, MyBreakpointProperties("out"))
    (bp1 as XBreakpointBase<*, *, *>).group = "TestGroup"

    assertTree("""
      Breakpoints
        239
          out.java:3
        TestGroup
          239
            in.java:2
    """)
  }

  @Test
  fun testTwoCustomGroupsSameType() {
    val bp1 = addLineBreakpoint(myBreakpointManager, "file:///a.java", 1, MyBreakpointProperties("a"))
    val bp2 = addLineBreakpoint(myBreakpointManager, "file:///b.java", 2, MyBreakpointProperties("b"))
    (bp1 as XBreakpointBase<*, *, *>).group = "GroupA"
    (bp2 as XBreakpointBase<*, *, *>).group = "GroupB"

    assertTree("""
      Breakpoints
        GroupA
          239
            a.java:2
        GroupB
          239
            b.java:3
    """)
  }

  @Test
  fun testLineAndFieldBreakpointsGroupedByType() {
    addLineBreakpoint(myBreakpointManager, "file:///a.java", 1, MyBreakpointProperties("a"))
    addFieldBreakpoint(myBreakpointManager, MyBreakpointProperties("myField"))

    assertTree("""
      Breakpoints
        239
          a.java:2
        Field Watchpoints
          myField
    """)
  }

  @Test
  fun testCustomGroupWithMixedTypes() {
    val bp1 = addLineBreakpoint(myBreakpointManager, "file:///a.java", 1, MyBreakpointProperties("a"))
    val bp2 = addFieldBreakpoint(myBreakpointManager, MyBreakpointProperties("myField"))
    (bp1 as XBreakpointBase<*, *, *>).group = "MyGroup"
    (bp2 as XBreakpointBase<*, *, *>).group = "MyGroup"

    assertTree("""
      Breakpoints
        MyGroup
          239
            a.java:2
          Field Watchpoints
            myField
    """)
  }

  @Test
  fun testMixedTypesGroupedAndUngrouped() {
    val bp1 = addLineBreakpoint(myBreakpointManager, "file:///a.java", 1, MyBreakpointProperties("a"))
    val bp2 = addFieldBreakpoint(myBreakpointManager, MyBreakpointProperties("myField"))
    addLineBreakpoint(myBreakpointManager, "file:///b.java", 2, MyBreakpointProperties("b"))
    addFieldBreakpoint(myBreakpointManager, MyBreakpointProperties("myOtherField"))
    (bp1 as XBreakpointBase<*, *, *>).group = "TestGroup"
    (bp2 as XBreakpointBase<*, *, *>).group = "TestGroup"

    assertTree("""
      Breakpoints
        239
          b.java:3
        Field Watchpoints
          myOtherField
        TestGroup
          239
            a.java:2
          Field Watchpoints
            myField
    """)
  }

  @Test
  fun testEmptyTree() {
    val provider = findProvider()
    awaitUpdate()
    val node = provider.createNode()
    if (node != null) {
      assertFalse("Tree should have no non-default breakpoints", hasTestBreakpoints(node))
    }
  }
}
