// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editorTabGrouping.impl

import com.intellij.mock.MockProjectEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Color
import java.util.Collections
import java.util.IdentityHashMap

@TestApplication
class EditorTabGroupingProviderTest {

  @TestDisposable
  private lateinit var disposable: Disposable

  private lateinit var project: MockProjectEx
  private lateinit var fileIndex: StubProjectFileIndex
  private var savedGroupByDirectory: Boolean = false

  @BeforeEach
  fun setUp() {
    project = MockProjectEx(disposable)
    fileIndex = StubProjectFileIndex()
    project.registerService(ProjectFileIndex::class.java, fileIndex)
    savedGroupByDirectory = EditorTabGroupingSettings.getInstance().groupByDirectory
    EditorTabGroupingSettings.getInstance().groupByDirectory = false
  }

  @AfterEach
  fun tearDown() {
    EditorTabGroupingSettings.getInstance().groupByDirectory = savedGroupByDirectory
  }

  @Test
  fun `resolution ignores the group by directory setting, so the caller can memoize it`() {
    val contentRoot = makeDir("/root")
    fileIndex.contentRootForFile = contentRoot
    val file = makeFile("/root/src/Foo.kt", "/root/src")

    EditorTabGroupingSettings.getInstance().groupByDirectory = false
    val disabled = EditorTabGroupingProvider.resolveDirectoryGroup(file, project)
    EditorTabGroupingSettings.getInstance().groupByDirectory = true
    val enabled = EditorTabGroupingProvider.resolveDirectoryGroup(file, project)

    assertEquals(enabled, disabled)
    assertNotNull(enabled)
  }

  @Test
  fun `directory grouping returns group with parent directory name as display name`() {
    EditorTabGroupingSettings.getInstance().groupByDirectory = true
    val contentRoot = makeDir("/project/root")
    fileIndex.contentRootForFile = contentRoot
    val file = makeFile("/project/root/src/main/kotlin/Foo.kt", "/project/root/src/main/kotlin")
    val group = EditorTabGroupingProvider.resolveDirectoryGroup(file, project)
    assertNotNull(group)
    assertEquals("kotlin", group!!.name, "display name is the immediate parent directory name")
  }

  @Test
  fun `directory grouping key includes absolute content root path`() {
    EditorTabGroupingSettings.getInstance().groupByDirectory = true
    val contentRoot = makeDir("/project/root")
    fileIndex.contentRootForFile = contentRoot
    val file = makeFile("/project/root/src/main/kotlin/Foo.kt", "/project/root/src/main/kotlin")
    val group = EditorTabGroupingProvider.resolveDirectoryGroup(file, project)!!
    assertTrue(group.key.startsWith("/project/root"), "key must contain the absolute content root path")
    assertTrue(group.key.contains("src/main/kotlin"), "key must contain the relative path")
  }

  @Test
  fun `directory grouping produces distinct keys for two roots with the same relative subpath`() {
    EditorTabGroupingSettings.getInstance().groupByDirectory = true

    val rootA = makeDir("/rootA")
    val fileA = makeFile("/rootA/src/main/Foo.kt", "/rootA/src/main")
    fileIndex.contentRootForFile = rootA
    val groupA = EditorTabGroupingProvider.resolveDirectoryGroup(fileA, project)

    val rootB = makeDir("/rootB")
    val fileB = makeFile("/rootB/src/main/Bar.kt", "/rootB/src/main")
    fileIndex.contentRootForFile = rootB
    val groupB = EditorTabGroupingProvider.resolveDirectoryGroup(fileB, project)

    assertNotNull(groupA)
    assertNotNull(groupB)
    assertNotEquals(groupA!!.key, groupB!!.key, "Different content roots must produce distinct group keys")
  }

  @Test
  fun `directory grouping returns null when file has no content root`() {
    EditorTabGroupingSettings.getInstance().groupByDirectory = true
    fileIndex.contentRootForFile = null
    val file = makeFile("/scratch/Foo.kt", "/scratch")
    assertNull(EditorTabGroupingProvider.resolveDirectoryGroup(file, project))
  }

  // --- AIR class-name detection ---

  @Test
  fun `isAirEditorTabClass matches AgentThreadViewVirtualFile`() {
    assertTrue(isAirEditorTabClass("com.intellij.air.thread.view.AgentThreadViewVirtualFile"))
  }

  @Test
  fun `isAirEditorTabClass matches AgentsPageVirtualFile`() {
    assertTrue(isAirEditorTabClass("com.intellij.air.frontend.agents.AgentsPageVirtualFile"))
  }

  @Test
  fun `isAirEditorTabClass does not match unrelated class names`() {
    assertFalse(isAirEditorTabClass("com.intellij.testFramework.LightVirtualFile"))
    assertFalse(isAirEditorTabClass("com.example.SomeOtherFile"))
    assertFalse(isAirEditorTabClass(""))
  }

  @Test
  fun `agentsTabGroup uses stable key and localized name`() {
    val group = agentsTabGroup()
    assertEquals("air:agents", group.key)
    assertEquals("Agents", group.name)
    assertEquals(AGENTS_GROUP_KEY, group.key)
  }

  // --- colorForGroup ---

  @Test
  fun `colorForGroup returns non-null color for any group`() {
    assertNotNull(EditorTabGroupingProvider.colorForGroup(TabGroup("myModule", "myModule")))
  }

  @Test
  fun `colorForGroup does not crash for extreme hash code values`() {
    // String whose hashCode() can be Int.MIN_VALUE: Math.floorMod must handle it
    val groups = listOf(
      TabGroup("", ""),
      TabGroup("a", "a"),
      TabGroup("z".repeat(100), "key"),
      TabGroup("\u0000", "null-char"),
    )
    for (g in groups) {
      assertNotNull(EditorTabGroupingProvider.colorForGroup(g), "null color for group ${g.name}")
    }
  }

  @Test
  fun `colorForGroup uses every color of the palette`() {
    // Compare by identity: the named colors can resolve to equal RGB values when the theme is not loaded.
    val used = Collections.newSetFromMap(IdentityHashMap<Color, Boolean>())
    for (i in 0 until 1000) used.add(EditorTabGroupingProvider.colorForGroup(TabGroup("g$i", "key$i")))
    assertEquals(EditorTabGroupingProvider.groupColors().size, used.size)
  }

  // --- helpers ---

  private fun makeDir(path: String): VirtualFile {
    val name = path.substringAfterLast('/', path)
    return object : LightVirtualFile(name) {
      override fun getPath(): String = path
      override fun isDirectory(): Boolean = true
      override fun getParent(): VirtualFile? = null
    }
  }

  private fun makeFile(filePath: String, parentPath: String): VirtualFile {
    val parent = makeDir(parentPath)
    val name = filePath.substringAfterLast('/')
    return object : LightVirtualFile(name) {
      override fun getPath(): String = filePath
      override fun isDirectory(): Boolean = false
      override fun getParent(): VirtualFile = parent
    }
  }
}
