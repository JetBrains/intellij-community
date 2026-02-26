// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.execution.junit.codeInspection.JUnitAssertEqualsMayBeAssertSameInspection
import com.intellij.junit.testFramework.MavenTestLib.JUNIT4
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.pom.java.LanguageLevel.Companion.HIGHEST
import com.intellij.testFramework.LightProjectDescriptor

private val descriptor = JUnitProjectDescriptor(HIGHEST, JUNIT4)

abstract class JUnitAssertEqualsMayBeAssertSameInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: JUnitAssertEqualsMayBeAssertSameInspection = JUnitAssertEqualsMayBeAssertSameInspection()
  override fun getProjectDescriptor(): LightProjectDescriptor = descriptor

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