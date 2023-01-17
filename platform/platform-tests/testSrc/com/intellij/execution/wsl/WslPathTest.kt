// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl

import com.intellij.openapi.vfs.impl.wsl.WslConstants
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test

class WslPathTest {

  private val PREFIX1 = WslConstants.UNC_PREFIX
  private val PREFIX2 = "\\\\wsl.localhost\\"

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
    checkParse("\\\\wsl$\\Ubuntu\\", WslPath(PREFIX1, "Ubuntu", "/"))
    checkParse("\\\\wsl$\\Ubuntu\\etc", WslPath(PREFIX1, "Ubuntu", "/etc"))
    checkParse("//wsl$/Ubuntu/etc", WslPath(PREFIX1, "Ubuntu", "/etc"))
    checkParse("//wsl$/Ubuntu\\etc", WslPath(PREFIX1, "Ubuntu", "/etc"))
  }

  @Test
  fun wsl_localhost() {
    checkParse("//wsl.localhost/Ubuntu/etc/hosts", WslPath(PREFIX2, "Ubuntu", "/etc/hosts"))
    checkParse("\\\\wsl.localhost\\Ubuntu\\etc\\hosts", WslPath(PREFIX2, "Ubuntu", "/etc/hosts"))
  }

  private fun checkParse(windowsPath: String, expectedWslPath: WslPath?) {
    Assert.assertEquals(expectedWslPath, WslPath.parseWindowsUncPath(windowsPath))
  }
}