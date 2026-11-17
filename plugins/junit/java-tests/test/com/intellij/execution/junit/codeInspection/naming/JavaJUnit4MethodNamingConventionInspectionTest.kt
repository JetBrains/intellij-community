// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection.naming

import com.intellij.junit.testFramework.JUnitProjectDescriptor
import com.intellij.junit.testFramework.MavenTestLib.JUNIT4
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.siyeh.ig.naming.NewMethodNamingConventionInspection
import org.junit.jupiter.api.Test

private val descriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST, JUNIT4)

@TestApplication
class JavaJUnit4MethodNamingConventionInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(descriptor)
  }

  private val myFixture by jvmInspectionFixture(projectFixture) {
    NewMethodNamingConventionInspection().apply {
      setEnabled(true, JUnit4MethodNamingConvention().shortName)
    }
  }

  @Test
  fun testJUnit4MethodNamingConvention() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.Test;

      public class JUnit4MethodNamingConvention {
        @Test
        public void <warning descr="JUnit 4+ test method name 'a' is too short (1 < 4)">a</warning>() {}

        @Test
        public void <warning descr="JUnit 4+ test method name 'abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz' is too long (78 > 64)">abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz</warning>() {}

        @Test
        public void <warning descr="JUnit 4+ test method name 'more${'$'}${'$'}${'$'}' doesn't match regex '[a-z][A-Za-z_\d]*'">more${'$'}${'$'}${'$'}</warning>() {}

        @Test
        public void assure_foo_is_never_null() {}
      }
    """.trimIndent())
  }
}