// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/nonDefaultConstructor")
@TestApplication
class NonDefaultConstructorInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture, "${DevkitJavaTestsUtil.TESTDATA_PATH}inspections/nonDefaultConstructor")

  @BeforeEach
  fun setUp() {

    myFixture.enableInspections(NonDefaultConstructorInspection())
  }

  @Test
  fun `test extends self`() {
    myFixture.testHighlighting("Foo.java")
  }

  @Test
  fun `test custom un-allowed constructor`() {
    myFixture.addClass("package com.intellij.codeInsight.completion; public class CompletionContributor {}")
    myFixture.testHighlighting("CustomConstructor.java")
  }

  @Test
  fun `test Project @Service`() {
    myFixture.addClass("package com.intellij.openapi.project; public class Project {}")
    myFixture.addClass("package com.intellij.openapi.components; public @interface Service {}")
    myFixture.testHighlighting("ProjectService.java")
  }

  @Test
  fun `test Project @Service with non allowed CTOR arg`() {
    myFixture.addClass("package com.intellij.openapi.project; public class Project {}")
    myFixture.addClass("package com.intellij.openapi.components; public @interface Service {}")
    myFixture.testHighlighting("ProjectServiceNonAllowedCtorArg.java")
  }

  @Test
  fun `test allow MessageBus and Project`() {
    myFixture.addClass("package com.intellij.openapi.components; public @interface Service {}")
    myFixture.addClass("package com.intellij.openapi.project; public class Project {}")
    myFixture.addClass("package com.intellij.util.messages; public class MessageBus {}")
    myFixture.testHighlighting("CustomConstructorMessageBusAndProject.java")
  }

  @Test
  fun `test allow MessageBus`() {
    myFixture.addClass("package com.intellij.openapi.components; public @interface Service {}")
    myFixture.addClass("package com.intellij.util.messages; public class MessageBus {}")
    myFixture.testHighlighting("CustomConstructorMessageBus.java")
  }

  @Test
  fun `test allow CoroutineScope`() {
    myFixture.addClass("package com.intellij.openapi.components; public @interface Service {}")
    myFixture.addClass("package com.intellij.openapi.project; public class Project {}")
    myFixture.addClass("package kotlinx.coroutines; public interface CoroutineScope {}")

    myFixture.testHighlighting("ServiceWithCoroutineScope.java")
  }
}