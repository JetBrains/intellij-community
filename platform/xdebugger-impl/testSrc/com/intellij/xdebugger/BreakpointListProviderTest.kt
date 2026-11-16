// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.ide.bookmark.BookmarksListProvider
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.debugger.impl.bookmarks.BreakpointListUpdaterService
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.xdebugger.XBreakpointsFixture.Companion.MY_LINE_BREAKPOINT_TYPE
import com.intellij.xdebugger.XBreakpointsFixture.Companion.MY_SIMPLE_BREAKPOINT_TYPE
import com.intellij.xdebugger.XBreakpointsFixture.MyBreakpointProperties
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for breakpoint tree structure in the Bookmarks tool window (IJPL-237810).
 */
@TestApplication
@RegistryKey(key = "ide.bookmark.show.all.breakpoints", value = "true")
class BreakpointListProviderTest {
  private val breakpoints by xBreakpointsFixture()

  @TestDisposable
  lateinit var disposable: Disposable

  private class MyFieldBreakpointType :
    XBreakpointType<XBreakpoint<MyBreakpointProperties>, MyBreakpointProperties>("testField", "Field Watchpoints") {
    override fun getDisplayText(breakpoint: XBreakpoint<MyBreakpointProperties>): String = breakpoint.properties?.myOption ?: ""
    override fun createProperties(): MyBreakpointProperties = MyBreakpointProperties()
  }

  private val myFieldBreakpointType = MyFieldBreakpointType()

  @BeforeEach
  fun setUp() {
    XBreakpointType.EXTENSION_POINT_NAME.point.registerExtension(myFieldBreakpointType, disposable)
    // Force lazy init of RootNode and its collection coroutine
    findProvider().createNode()
  }

  private fun addFieldBreakpoint(
    breakpointManager: XBreakpointManagerImpl,
    properties: MyBreakpointProperties,
  ): XBreakpoint<MyBreakpointProperties> =
    breakpointManager.addBreakpoint(myFieldBreakpointType, properties)

  private fun findProvider(): BookmarksListProvider =
    BookmarksListProvider.EP.getExtensions(breakpoints.project).first { it.javaClass.name.contains("BreakpointListProvider") }

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

  /**
   * Waits until the frontend breakpoint manager has each test breakpoint of the backend, with the same group.
   * The frontend gets the backend changes asynchronously, and the tree reads the frontend.
   */
  private fun awaitFrontend() {
    val testTypes = setOf(MY_LINE_BREAKPOINT_TYPE, MY_SIMPLE_BREAKPOINT_TYPE, myFieldBreakpointType)
    val expected = runReadActionBlocking { breakpoints.breakpointManager.allBreakpoints }
      .filter { it.type in testTypes }
      .associate { it.breakpointId to it.group }
    val proxy = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(breakpoints.project)
    val changes = Channel<Unit>(Channel.CONFLATED)
    val subscription = Disposer.newDisposable()
    proxy.subscribeOnBreakpointsChanges(subscription) { changes.trySend(Unit) }
    try {
      runBlockingMaybeCancellable {
        withTimeout(10.seconds) {
          while (true) {
            val actual = proxy.getAllBreakpoints().associate { it.id to it.getGroup() }
            if (expected.all { it.key in actual && actual[it.key] == it.value }) break
            changes.receive()
          }
        }
      }
    }
    finally {
      Disposer.dispose(subscription)
    }
  }

  /**
   * Waits until the breakpoint list shows the current breakpoints of the backend.
   * The function waits for the frontend, starts a new update of the list, and waits for the update job that is current at its end.
   * A breakpoint change cancels the update job and starts a new one, which also reads the breakpoints after the frontend wait.
   */
  private fun awaitUpdate() {
    awaitFrontend()
    val service = breakpoints.project.service<BreakpointListUpdaterService>()
    runBlockingMaybeCancellable {
      withContext(Dispatchers.EDT) {
        service.requestUpdate()
      }
      while (true) {
        val job = service.updateJob ?: break
        job.join()
        if (!job.isCancelled && service.updateJob === job) break
      }
    }
  }

  @Test
  fun testGroupedByType() {
    breakpoints.addLineBreakpoint("file:///a.java", 1, MyBreakpointProperties("a"))
    breakpoints.addLineBreakpoint("file:///b.java", 2, MyBreakpointProperties("b"))

    assertTree("""
      Breakpoints
        239
          b.java:3
          a.java:2
    """)
  }

  @Test
  fun testCustomGroupNesting() {
    val bp = breakpoints.addLineBreakpoint("file:///a.java", 1, MyBreakpointProperties("a"))
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
    val bp1 = breakpoints.addLineBreakpoint("file:///in.java", 1, MyBreakpointProperties("in"))
    breakpoints.addLineBreakpoint("file:///out.java", 2, MyBreakpointProperties("out"))
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
    val bp1 = breakpoints.addLineBreakpoint("file:///a.java", 1, MyBreakpointProperties("a"))
    val bp2 = breakpoints.addLineBreakpoint("file:///b.java", 2, MyBreakpointProperties("b"))
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
    breakpoints.addLineBreakpoint("file:///a.java", 1, MyBreakpointProperties("a"))
    addFieldBreakpoint(breakpoints.breakpointManager, MyBreakpointProperties("myField"))

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
    val bp1 = breakpoints.addLineBreakpoint("file:///a.java", 1, MyBreakpointProperties("a"))
    val bp2 = addFieldBreakpoint(breakpoints.breakpointManager, MyBreakpointProperties("myField"))
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
    val bp1 = breakpoints.addLineBreakpoint("file:///a.java", 1, MyBreakpointProperties("a"))
    val bp2 = addFieldBreakpoint(breakpoints.breakpointManager, MyBreakpointProperties("myField"))
    breakpoints.addLineBreakpoint("file:///b.java", 2, MyBreakpointProperties("b"))
    addFieldBreakpoint(breakpoints.breakpointManager, MyBreakpointProperties("myOtherField"))
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
      assertFalse(hasTestBreakpoints(node), "Tree should have no non-default breakpoints")
    }
  }
}
