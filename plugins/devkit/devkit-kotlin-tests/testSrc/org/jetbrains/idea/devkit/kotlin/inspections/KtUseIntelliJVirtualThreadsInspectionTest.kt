// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.quickfix.UseIntelliJVirtualThreadsInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath($$"$CONTENT_ROOT/testData/inspections/useIntelliJVirtualThreads")
class KtUseIntelliJVirtualThreadsInspectionTest : UseIntelliJVirtualThreadsInspectionTestBase() {
  override fun getBasePath(): String {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/useIntelliJVirtualThreads"
  }

  fun testUseIntelliJVirtualThreads() {
    doTest("Replace with 'IntelliJVirtualThreads.ofVirtual()'")
  }

  override fun getFileExtension(): String = "kt"
}
