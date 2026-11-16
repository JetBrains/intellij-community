// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.quickfix.virtualFileUrlConversionFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath($$"$CONTENT_ROOT/testData/inspections/virtualFileUrlConversion")
@TestApplication
class KtVirtualFileUrlConversionInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val urlConversion by virtualFileUrlConversionFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/virtualFileUrlConversion",
                                                   addVirtualFileUrlStubs = false)
  private val myFixture get() = urlConversion.fixture

  @BeforeEach
  fun setUp() {
    myFixture.addFileToProject(
      "src/com/intellij/platform/workspace/storage/url/VirtualFileUrl.kt",
      """
        package com.intellij.platform.workspace.storage.url

        interface VirtualFileUrl {
          val url: String
        }

        interface VirtualFileUrlManager {
          fun storeAndGet(url: String): VirtualFileUrl
        }
      """.trimIndent()
    )
  }

  @Test
  fun testConversions() {
    urlConversion.doTest()
  }
}
