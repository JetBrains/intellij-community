// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection.naming

import com.intellij.junit.testFramework.JUnitProjectDescriptor
import com.intellij.junit.testFramework.MavenTestLib
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.codeInspection.naming.NewGroovyClassNamingConventionInspection
import org.junit.jupiter.api.Test

private val descriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST, MavenTestLib.JUNIT3)

@TestApplication
class GroovyJUnit3NamingConventionInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(descriptor)
  }

  private val myFixture by jvmInspectionFixture(projectFixture) {
    NewGroovyClassNamingConventionInspection().apply {
      setEnabled(true, TestClassNamingConvention.TEST_CLASS_NAMING_CONVENTION_SHORT_NAME)
      setEnabled(true, AbstractTestClassNamingConvention.ABSTRACT_TEST_CLASS_NAMING_CONVENTION_SHORT_NAME)
    }
  }

  @Test
  fun testTestCaseConvention() {
    myFixture.testHighlighting(JvmLanguage.GROOVY, """
      import junit.framework.TestCase

      class SpecialGoodTest extends TestCase {
        class MyVeryInner extends SpecialGoodTest {}
      }
      class <warning descr="Test class name 'SpecialBad' doesn't match regex '[A-Z][A-Za-z\d]*Test(s|Case)?|Test[A-Z][A-Za-z\d]*|IT(.*)|(.*)IT(Case)?'">SpecialBad</warning> extends TestCase { }
      class TestInTheBeginning extends TestCase { }
      class WithTests extends TestCase { }
      class WithTestCase extends TestCase { }
      abstract class <warning descr="Abstract test class name 'SpecialAbstract' doesn't match regex '[A-Z][A-Za-z\d]*TestCase'">SpecialAbstract</warning> extends TestCase { }
      abstract class SpecialAbstractTestCase extends TestCase { }
    """.trimIndent())
  }
}