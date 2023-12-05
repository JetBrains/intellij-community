// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.scale

import com.android.tools.adtui.webp.WebpMetadata
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.icons.loadRasterImage
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class ImageFormatLoadTest {

  @Test
  fun testLoadingPng() {
    loadRasterImage(FileInputStream(File(PlatformTestUtil.getPlatformTestDataPath() + "ui/db_set_breakpoint.png")))
  }

  @Test
  fun testLoadingJpg() {
    loadRasterImage(FileInputStream(File(PlatformTestUtil.getPlatformTestDataPath() + "ui/jetbrains_logo.jpg")))
  }

  @Test
  fun testLoadingWebp() {
    WebpMetadata.ensureWebpRegistered()
    loadRasterImage(FileInputStream(File(PlatformTestUtil.getPlatformTestDataPath() + "ui/jetbrains_logo.webp")))
  }
}