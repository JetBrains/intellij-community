// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection.naming

import com.intellij.junit.testFramework.JUnitProjectDescriptor
import com.intellij.junit.testFramework.MavenTestLib.JUNIT3
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.siyeh.ig.naming.NewMethodNamingConventionInspection
import org.junit.jupiter.api.Test

private val descriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST, JUNIT3)

@TestApplication
class JavaJUnit3MethodNamingConventionInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(descriptor)
  }

  private val myFixture by jvmInspectionFixture(projectFixture) {
    NewMethodNamingConventionInspection().apply {
      setEnabled(true, JUnit3MethodNamingConvention().shortName)
    }
  }

  @Test
  fun testJUnit3MethodNamingConvention() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class JUnit3MethodNamingConvention extends junit.framework.TestCase {
        public void <warning descr="JUnit 3 test method name 'testA' is too short (5 < 8)">testA</warning>() {}
        public void <warning descr="JUnit 3 test method name 'testAbcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz' is too long (82 > 64)">testAbcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz</warning>() {}
        public void <warning descr="JUnit 3 test method name 'testGiveMeMore${'$'}${'$'}${'$'}' doesn't match regex 'test[A-Za-z_\d]*'">testGiveMeMore${'$'}${'$'}${'$'}</warning>() {}
        public void test_me_properly() {}
      }
    """.trimIndent())
  }
}