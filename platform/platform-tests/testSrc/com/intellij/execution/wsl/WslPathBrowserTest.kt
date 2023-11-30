// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.wsl.ui.createFileChooserDescriptor
import com.intellij.execution.wsl.ui.getBestWindowsPathFromLinuxPath
import com.intellij.testFramework.fixtures.TestFixtureRule
import org.junit.Assert.*
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
    assertEquals("${WSLUtil.getUncPrefix()}${wslRule.wsl.msId}\\bin", getBestWindowsPathFromLinuxPath(wslRule.wsl, "/bin")!!.presentableUrl)
  }

  @Test
  fun bestPath() {
    assertEquals("${WSLUtil.getUncPrefix()}${wslRule.wsl.msId}\\bin", getBestWindowsPathFromLinuxPath(wslRule.wsl, "/bin/foo/bur/buz")!!.presentableUrl)
  }

  @Test
  fun wrongPath() {
    assertEquals("${WSLUtil.getUncPrefix()}${wslRule.wsl.msId}\\", getBestWindowsPathFromLinuxPath(wslRule.wsl, "/something_that_simply_doesnt_exist")!!.presentableUrl)
  }

  @Test
  fun unparsablePath() {
    assertNull(getBestWindowsPathFromLinuxPath(wslRule.wsl, "c:\\windows"))
  }

  @Test
  fun fileDescriptorWithWindows() {
    val rootDrives = listWindowsLocalDriveRoots()
    wslRule.wsl.executeOnWsl(20_000, "ls").exitCode // To reanimate wsl in case of failure
    val roots = createFileChooserDescriptor(wslRule.wsl, true).roots.map { it.toNioPath() }
    assertEquals("Wrong number of roots: ${roots}, while root drives are: ${rootDrives}", rootDrives.count() + 1, roots.size)
    for (root in roots) {
      if (root != wslRule.wsl.getUNCRootPath() && root !in rootDrives) {
        fail("Unexpected root $root")
      }
    }
  }

  @Test
  fun fileDescriptorNoWindows() {
    val roots = createFileChooserDescriptor(wslRule.wsl, false).roots.map { it.toNioPath() }
    assertEquals(roots.toList(), listOf(wslRule.wsl.getUNCRootPath()))
  }
}
