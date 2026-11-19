// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/simplifiableServiceRetrieving")
@TestApplication
internal class SimplifiableServiceRetrievingInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val simplifiable by simplifiableServiceRetrievingFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/simplifiableServiceRetrieving/")

  @Test
  fun testReplaceWithGetInstanceApplicationLevel() {
    simplifiable.doTest(DevKitBundle.message("inspection.simplifiable.service.retrieving.replace.with", "MyService", "getInstance"))
  }

  @Test
  fun testReplaceWithGetInstanceProjectLevel() {
    simplifiable.doTest(DevKitBundle.message("inspection.simplifiable.service.retrieving.replace.with", "MyService", "getInstance"))
  }

  @Test
  fun testTooGenericGetInstanceReturnType() {
    simplifiable.doTest()
  }

  @Test
  fun testNullableGetInstanceMethod() {
    simplifiable.doTest()
  }

  @Test
  fun testReturnExpressionOfNullableMethod() {
    simplifiable.doTest()
  }

  @Test
  fun testNullableVariable() {
    simplifiable.doTest()
  }
}
