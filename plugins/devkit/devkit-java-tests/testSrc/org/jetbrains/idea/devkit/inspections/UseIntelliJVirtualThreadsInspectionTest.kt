// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.UseIntelliJVirtualThreadsInspectionTestBase

@TestDataPath($$"$CONTENT_ROOT/testData/inspections/useIntelliJVirtualThreads")
class UseIntelliJVirtualThreadsInspectionTest : UseIntelliJVirtualThreadsInspectionTestBase() {
  override fun getBasePath(): String {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/useIntelliJVirtualThreads"
  }

  fun testUseIntelliJVirtualThreads() {
    doTest("Replace with 'IntelliJVirtualThreads.ofVirtual()'")
  }

  override fun getFileExtension(): String = "java"
}
