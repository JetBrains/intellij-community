// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath

import com.intellij.jsonpath.inspections.JsonPathUnknownFunctionInspection
import com.intellij.jsonpath.inspections.JsonPathUnknownOperatorInspection
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.sharedCodeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.sharedCodeInsightProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JsonPathInspectionTest {
  companion object {
    private val projectFixture = sharedCodeInsightProjectFixture()
  }

  private val myFixture by sharedCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(
      JsonPathUnknownFunctionInspection::class.java,
      JsonPathUnknownOperatorInspection::class.java
    )
  }

  @Test
  fun sizeIsKnownFunction() {
    myFixture.configureByText(JsonPathFileType.INSTANCE, "$.size()")
    myFixture.checkHighlighting()
  }

  @Test
  fun unknownFunction() {
    myFixture.configureByText(JsonPathFileType.INSTANCE, "\$.<warning descr=\"Unknown function name 'unknown'\">unknown</warning>()")
    myFixture.checkHighlighting()
  }

  @Test
  fun unknownOperator() {
    myFixture.configureByText(JsonPathFileType.INSTANCE, "$[?(@.id <warning descr=\"Unknown operator 'unknown_operator'\">unknown_operator</warning> 2)]")
    myFixture.checkHighlighting()
  }
}