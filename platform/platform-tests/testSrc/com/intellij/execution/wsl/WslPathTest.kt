// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl

import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test

class WslPathTest {

  @Before
  fun setUp() {
    Assume.assumeTrue(WSLUtil.isSystemCompatible())
  }

  @Test
  fun testParse() {
    checkParse("", null)
    checkParse("/mnt/c/usr/bin", null)
    checkParse("C:\\Users\\user", null)
    checkParse("\\\\wsl$", null)
    checkParse("\\\\wsl$\\", null)
    checkParse("\\\\wsl$\\Ubuntu", null)
    checkParse("\\\\wsl$\\\\etc", null)
    checkParse("\\\\wsl$\\Ubuntu\\", WslPath("Ubuntu", "/"))
    checkParse("\\\\wsl$\\Ubuntu\\etc", WslPath("Ubuntu", "/etc"))
    checkParse("//wsl$/Ubuntu/etc", WslPath("Ubuntu", "/etc"))
    checkParse("//wsl$/Ubuntu\\etc", WslPath("Ubuntu", "/etc"))
  }

  private fun checkParse(windowsPath: String, expectedWslPath: WslPath?) {
    Assert.assertEquals(expectedWslPath, WslPath.parseWindowsUncPath(windowsPath))
  }
}