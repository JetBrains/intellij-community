// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.codeInsight

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.siyeh.ig.LightJavaInspectionTestCase

class JUnit5MalformedOrderedTest : LightJavaInspectionTestCase() {
  override fun getInspection(): InspectionProfileEntry? {
    return JUnit5MalformedOrderedTestInspection()
  }

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture)
    addEnvironmentClass("""
      import org.junit.jupiter.api.*;
      class MyOrderer implements MethodOrderer {
        public void orderMethods(MethodOrdererContext context) {}
      }
      """)
  }

  fun testInvalidOrder() {
    doTest()
    for (fix in myFixture.getAllQuickFixes()) {
      myFixture.launchAction(fix)
    }
    myFixture.checkResultByFile(getTestName(false) + "_after.java")
  }

  fun testAllOrderableTestTypes() {
    doTest()
    for (fix in myFixture.getAllQuickFixes()) {
      myFixture.launchAction(fix)
    }
    myFixture.checkResultByFile(getTestName(false) + "_after.java")
  }

  fun testInheritedOrderTest() {
    doTest()
    for (fix in myFixture.getAllQuickFixes()) {
      myFixture.launchAction(fix)
    }
    myFixture.checkResultByFile(getTestName(false) + "_after.java")
  }

  fun testInterfacedOrderTest() {
    doTest()
    for (fix in myFixture.getAllQuickFixes()) {
      myFixture.launchAction(fix)
    }
    myFixture.checkResultByFile(getTestName(false) + "_after.java")
  }

  fun testTestsAtParentOnly() {
    doTest()
    for (fix in myFixture.getAllQuickFixes()) {
      myFixture.launchAction(fix)
    }
    myFixture.checkResultByFile(getTestName(false) + "_after.java")
  }

  fun testValidOrder() {
    doTest()
  }

  override fun getBasePath(): String {
    return "/plugins/junit/testData/codeInsight/malformedOrdered"
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return LightJavaCodeInsightFixtureTestCase.JAVA_8
  }
}
