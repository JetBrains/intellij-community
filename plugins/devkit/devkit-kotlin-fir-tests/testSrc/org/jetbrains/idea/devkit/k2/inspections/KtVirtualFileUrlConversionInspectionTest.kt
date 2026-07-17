// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.quickfix.VirtualFileUrlConversionInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath($$"$CONTENT_ROOT/testData/inspections/virtualFileUrlConversion")
class KtVirtualFileUrlConversionInspectionTest : VirtualFileUrlConversionInspectionTestBase() {
  override fun getBasePath(): String {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/virtualFileUrlConversion"
  }

  override fun getFileExtension(): String = "kt"

  override fun addVirtualFileUrlStubs() {
    myFixture.addFileToProject(
      "src/com/intellij/platform/workspace/storage/url/VirtualFileUrl.kt",
      """
        package com.intellij.platform.workspace.storage.url

        interface VirtualFileUrl {
          val url: String
        }

        interface VirtualFileUrlManager {
          fun getOrCreateFromUrl(url: String): VirtualFileUrl
        }
      """.trimIndent()
    )
  }

  fun testConversions() {
    doTest()
  }
}
