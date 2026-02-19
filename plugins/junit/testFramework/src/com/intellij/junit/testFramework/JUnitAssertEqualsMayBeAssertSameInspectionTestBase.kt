// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.execution.junit.codeInspection.JUnitAssertEqualsMayBeAssertSameInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class JUnitAssertEqualsMayBeAssertSameInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: JUnitAssertEqualsMayBeAssertSameInspection = JUnitAssertEqualsMayBeAssertSameInspection()
  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST, JUnitLibrary.JUNIT4)

  override fun setUp() {
    super.setUp()
    @Suppress("InstantiationOfUtilityClass")
    myFixture.addClass("""
      final class A {
        public static final A a = new A(); 
        public static final A b = a; 
      }
    """.trimIndent())
  }
}