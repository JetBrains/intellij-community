// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.PlatformTestUtil
import org.junit.Test

class CustomIconLoadingTest {
  @Test
  fun loadIconByFileName() {
    val path = PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_OffsetIcon@1x.png"
    doTest(path)
  }

  @Test
  fun loadIconByFileUrl() {
    val url = "file:" + PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_OffsetIcon@1x.png"
    doTest(url)
  }

  @Test
  fun loadIconFromJar() {
    val url = "jar:file:" + PlatformTestUtil.getPlatformTestDataPath() + "ui/iconsJar.jar!/icons/gold_OffsetIcon@1x.png"
    doTest(url)
  }

  private fun doTest(path: String) {
    IconLoader.activate()
    try {
      CustomActionsSchema.loadCustomIcon(path)
    }
    finally {
      IconLoader.deactivate()
    }
  }
}