// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import org.junit.*

@Ignore
class VirtualFileUrlManagerTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManager.getInstance(projectModel.project)
  }

  @Test
  fun `check isEqualOrParentOf`() {
    assertIsEqualOrParentOf(true, "temp:///src", "temp:///src/my")
    assertIsEqualOrParentOf(true, "temp:///src", "temp:///src/my/")
    assertIsEqualOrParentOf(false, "temp:///src", "temp:///srC/my")
    assertIsEqualOrParentOf(false, "temp:///src/x", "temp:///src/y")
    assertIsEqualOrParentOf(false, "file:///src/my", "temp:///src/my")
    assertIsEqualOrParentOf(false, "file:///src/my", "temp:///src/my")
    assertIsEqualOrParentOf(false, "", "temp:///src/my")
    assertIsEqualOrParentOf(false, "temp:///src/my", "")
    assertIsEqualOrParentOf(true, "temp://", "temp:///src/my")
  }

  private fun assertIsEqualOrParentOf(expectedResult: Boolean, parentString: String, childString: String) {
    val parent = virtualFileManager.getOrCreateFromUri(parentString)
    val child = virtualFileManager.getOrCreateFromUri(childString)
    Assert.assertTrue("'$parent'.isEqualOrParentOf('$parent')", parent.isEqualOrParentOf(parent))
    Assert.assertTrue("'$child'.isEqualOrParentOf('$child')", child.isEqualOrParentOf(child))
    Assert.assertEquals(
      "'$parent'.isEqualOrParentOf('$child') should be ${if (expectedResult) "true" else "false"}",
      expectedResult,
      parent.isEqualOrParentOf(child))
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}
