// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.codeInsight

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.siyeh.ig.LightInspectionTestCase

class JUnit5MalformedNestedClassTest : LightInspectionTestCase() {
  override fun getInspection(): InspectionProfileEntry? {
    return JUnit5MalformedNestedClassInspection()
  }

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    addEnvironmentClass("package org.junit.jupiter.api;" +
                        "public @interface Nested {}")
  }

  fun testMalformed() {
    doTest()
  }

  override fun getBasePath(): String {
    return "/plugins/junit/testData/codeInsight/malformedNested"
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return LightCodeInsightFixtureTestCase.JAVA_8
  }
}
