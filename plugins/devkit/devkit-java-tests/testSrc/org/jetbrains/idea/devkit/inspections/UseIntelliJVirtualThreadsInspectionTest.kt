// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.useIntelliJVirtualThreadsFixture
import org.jetbrains.idea.devkit.inspections.quickfix.useIntelliJVirtualThreadsProjectFixture
import org.junit.jupiter.api.Test

@TestDataPath($$"$CONTENT_ROOT/testData/inspections/useIntelliJVirtualThreads")
@TestApplication
class UseIntelliJVirtualThreadsInspectionTest {
  companion object {
    private val projectFixture = useIntelliJVirtualThreadsProjectFixture()
  }

  private val virtualThreads by useIntelliJVirtualThreadsFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/useIntelliJVirtualThreads")

  @Test
  fun testUseIntelliJVirtualThreads() {
    virtualThreads.doTest("Replace with 'IntelliJVirtualThreads.ofVirtual()'")
  }
}
