// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase
import org.jetbrains.idea.devkit.inspections.quickfix.UseIntelliJVirtualThreadsInspectionTestBase
import java.util.function.Supplier

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
