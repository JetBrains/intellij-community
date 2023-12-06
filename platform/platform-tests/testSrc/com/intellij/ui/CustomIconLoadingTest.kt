// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.ui.customization.loadCustomIcon
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.PlatformTestUtil
import org.junit.Test

class CustomIconLoadingTest {
  @Test
  fun `load icon by default file name`() {
    doTest(PlatformTestUtil.getPlatformTestDataPath() + "ui/db_set_breakpoint.png")
  }

  @Test
  fun `load icon by _dark file name`() {
    doTest(PlatformTestUtil.getPlatformTestDataPath() + "ui/db_set_breakpoint_dark.png")
  }

  @Test
  fun `load icon by @2x file name`() {
    doTest(PlatformTestUtil.getPlatformTestDataPath() + "ui/db_set_breakpoint@2x.png")
  }

  @Test
  fun `load icon by @2x_dark file name`() {
    doTest(PlatformTestUtil.getPlatformTestDataPath() + "ui/db_set_breakpoint@2x_dark.png")
  }

  @Test
  fun loadIconByFileUrl() {
    val url = "file:" + PlatformTestUtil.getPlatformTestDataPath() + "ui/db_set_breakpoint.png"
    doTest(url)
  }

  @Test
  fun loadIconFromJar() {
    val url = "jar:file:" + PlatformTestUtil.getPlatformTestDataPath() + "ui/iconsJar.jar!/icons/db_set_breakpoint.png"
    doTest(url)
  }

  private fun doTest(path: String) {
    IconLoader.activate()
    try {
      loadCustomIcon(path)
    }
    finally {
      IconLoader.deactivate()
    }
  }
}