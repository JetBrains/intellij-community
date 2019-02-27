// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

class NonDefaultConstructorInspectionTest : LightCodeInsightFixtureTestCase() {
  override fun getBasePath() = "${DevkitJavaTestsUtil.TESTDATA_PATH}inspections/nonDefaultConstructor"

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    myFixture.enableInspections(NonDefaultConstructorInspection())
  }

  fun `test extends self`() {
    myFixture.testHighlighting("Foo.java")
  }

  fun `test custom un-allowed constructor`() {
    myFixture.addClass("package com.intellij.openapi.extensions; class AbstractExtensionPointBean {}")
    myFixture.testHighlighting("CustomConstructor.java")
  }
}