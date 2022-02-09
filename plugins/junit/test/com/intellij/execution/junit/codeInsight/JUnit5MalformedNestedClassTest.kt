// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInsight

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.siyeh.ig.LightJavaInspectionTestCase

class JUnit5MalformedNestedClassTest : LightJavaInspectionTestCase() {
  override fun getInspection(): InspectionProfileEntry {
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
    return LightJavaCodeInsightFixtureTestCase.JAVA_8
  }
}
