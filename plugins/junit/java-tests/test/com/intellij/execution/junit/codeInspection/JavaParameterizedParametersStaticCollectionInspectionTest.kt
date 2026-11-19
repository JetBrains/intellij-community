// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.execution.JUnitBundle
import com.intellij.junit.testFramework.JUnitProjectDescriptor
import com.intellij.junit.testFramework.MavenTestLib.JUNIT4
import com.intellij.junit.testFramework.MavenTestLib.JUNIT5
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private val descriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST, JUNIT4, JUNIT5)

@TestApplication
class JavaParameterizedParametersStaticCollectionInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(descriptor)
  }

  private val myFixture by jvmInspectionFixture(projectFixture) { ParameterizedParametersStaticCollectionInspection() }

  @Test
  fun testCreateMethod() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
      public class <warning descr="Parameterized test class 'CreateMethod' lacks data provider method annotated with '@Parameters'"><caret>CreateMethod</warning> { }
    """.trimIndent())
  }

  @Test
  fun testCreateMethodFix() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
      public class Create<caret>Method { }
    """.trimIndent(), """
      import org.junit.runners.Parameterized;

      @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
      public class CreateMethod {
          @Parameterized.Parameters
          public static Iterable<Object[]> parameters() {
              return null;
          }
      }
    """.trimIndent(), JUnitBundle.message("fix.data.provider.create.method.fix.name"))
  }

  @Test
  fun testWrongSignature() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
      public class WrongSignature {
          @org.junit.runners.Parameterized.Parameters
          java.util.Collection <warning descr="Data provider method 'regExValues()' has an incorrect signature"><caret>regExValues</warning>() {
              return null;
          }
      }
    """.trimIndent())
  }

  @Test
  fun testWrongSignatureFix() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
      public class WrongSignature {
          @org.junit.runners.Parameterized.Parameters
          java.util.Collection regEx<caret>Values() {
              return null;
          }
      }
    """.trimIndent(), """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
      public class WrongSignature {
          @org.junit.runners.Parameterized.Parameters
          public static java.util.Collection regExValues() {
              return null;
          }
      }
    """.trimIndent(), JUnitBundle.message("fix.data.provider.signature.fix.name", "public static Collection regExValues()"))
  }

  @Test
  fun testWrongSignature1() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
      public class WrongSignature1 {
         @org.junit.runners.Parameterized.Parameters
          static Integer <warning descr="Data provider method 'regExValues()' has an incorrect signature">regExValues</warning>() {
              return null;
          }
      }
    """.trimIndent())
  }

  @Test
  fun testWrongSignature2() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
      public class WrongSignature2 {
         @org.junit.runners.Parameterized.Parameters
         public static Integer <warning descr="Data provider method 'regExValues()' has an incorrect signature">regExValues</warning>() {
              return null;
          }
      }
    """.trimIndent())
  }

  @Test
  fun testWrongSignature3() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
      public class WrongSignature3 {
         @org.junit.runners.Parameterized.Parameters
         public static Integer[] <warning descr="Data provider method 'regExValues()' has an incorrect signature">regExValues</warning>() {
              return null;
          }
      }
    """.trimIndent())
  }

  @Test
  fun testCorrectSignature() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
      public class CorrectSignature {
        @org.junit.runners.Parameterized.Parameters
        public static Object[] parameters() {
          return null;
        }
      }
    """.trimIndent())
  }

  @Test
  fun testCorrectSignature2() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
      public class CorrectSignature2 {
        @org.junit.runners.Parameterized.Parameters
        public static Iterable<Object[]> parameters() {
          return null;
        }
      }
    """.trimIndent())
  }

  @Test
  fun testMultipleMethods() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
      public class <warning descr="Multiple @Parameters data provider methods present in class 'MultipleMethods'">MultipleMethods</warning> {

        @org.junit.runners.Parameterized.Parameters
        public static Object[] parameters() {
          return null;
        }
        @org.junit.runners.Parameterized.Parameters
        public static Object[] parameters2() {
          return null;
        }
      }
    """.trimIndent())
  }
}