// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl

import com.intellij.execution.wsl.ui.createFileChooserDescriptor
import com.intellij.execution.wsl.ui.getBestWindowsPathFromLinuxPath
import com.intellij.testFramework.fixtures.TestFixtureRule
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Test various logic for [com.intellij.execution.wsl.ui.WslPathBrowser]
 */
class WslPathBrowserTest {
  companion object {
    private val appRule = TestFixtureRule()
    private val wslRule = WslRule()

    @ClassRule
    @JvmField
    val ruleChain: RuleChain = RuleChain.outerRule(appRule).around(wslRule)
  }

  @Test
  fun correctPath() {
    val winPath = getBestWindowsPathFromLinuxPath(wslRule.wsl, "/bin")!!
    Assert.assertEquals("file:////wsl$/${wslRule.wsl.msId}/bin", winPath.toString())
  }

  @Test
  fun bestPath() {
    val winPath = getBestWindowsPathFromLinuxPath(wslRule.wsl, "/bin/foo/bur/buz")!!
    Assert.assertEquals("file:////wsl$/${wslRule.wsl.msId}/bin", winPath.toString())
  }

  @Test
  fun wrongPath() {
    val winPath = getBestWindowsPathFromLinuxPath(wslRule.wsl, "/something_that_simply_doesnt_exist")
    Assert.assertEquals("file:////wsl$/${wslRule.wsl.msId}/", winPath.toString())
  }

  @Test
  fun unparsablePath() {
    val winPath = getBestWindowsPathFromLinuxPath(wslRule.wsl, "c:\\windows")
    Assert.assertNull(winPath)
  }

  @Test
  fun fileDescriptorWithWindows() {
    val rootDrives = listWindowsLocalDriveRoots()
    wslRule.wsl.executeOnWsl(20_000, "ls").exitCode // To reanimate wsl in case of failure
    val roots = createFileChooserDescriptor(wslRule.wsl, true).roots.map { it.toNioPath() }
    Assert.assertEquals("Wrong number of roots: ${roots}, while root drives are: ${rootDrives}", rootDrives.count() + 1, roots.size)
    for (root in roots) {
      if (root != wslRule.wsl.getUNCRootPath() && root !in rootDrives) {
        Assert.fail("Unexpected root $root")
      }
    }
  }

  @Test
  fun fileDescriptorNoWindows() {
    val roots = createFileChooserDescriptor(wslRule.wsl, false).roots.map { it.toNioPath() }
    Assert.assertEquals(roots.toList(), listOf(wslRule.wsl.getUNCRootPath()))
  }
}