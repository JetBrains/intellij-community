// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/nonDefaultConstructor")
class NonDefaultConstructorInspectionTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath() = "${DevkitJavaTestsUtil.TESTDATA_PATH}inspections/nonDefaultConstructor"

  override fun setUp() {
    super.setUp()

    myFixture.enableInspections(NonDefaultConstructorInspection())
  }

  fun `test extends self`() {
    myFixture.testHighlighting("Foo.java")
  }

  fun `test custom un-allowed constructor`() {
    myFixture.addClass("package com.intellij.codeInsight.completion; public class CompletionContributor {}")
    myFixture.testHighlighting("CustomConstructor.java")
  }

  fun `test Project @Service`() {
    myFixture.addClass("package com.intellij.openapi.project; public class Project {}")
    myFixture.addClass("package com.intellij.openapi.components; public @interface Service {}")
    myFixture.testHighlighting("ProjectService.java")
  }

  fun `test Project @Service with non allowed CTOR arg`() {
    myFixture.addClass("package com.intellij.openapi.project; public class Project {}")
    myFixture.addClass("package com.intellij.openapi.components; public @interface Service {}")
    myFixture.testHighlighting("ProjectServiceNonAllowedCtorArg.java")
  }

  fun `test allow MessageBus and Project`() {
    myFixture.addClass("package com.intellij.openapi.components; public @interface Service {}")
    myFixture.addClass("package com.intellij.openapi.project; public class Project {}")
    myFixture.addClass("package com.intellij.util.messages; public class MessageBus {}")
    myFixture.testHighlighting("CustomConstructorMessageBusAndProject.java")
  }

  fun `test allow MessageBus`() {
    myFixture.addClass("package com.intellij.openapi.components; public @interface Service {}")
    myFixture.addClass("package com.intellij.util.messages; public class MessageBus {}")
    myFixture.testHighlighting("CustomConstructorMessageBus.java")
  }
}