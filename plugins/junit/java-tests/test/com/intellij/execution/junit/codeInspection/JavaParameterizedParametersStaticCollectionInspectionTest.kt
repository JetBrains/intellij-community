// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.execution.JUnitBundle
import com.intellij.junit.testFramework.JUnitLibrary
import com.intellij.junit.testFramework.JUnitProjectDescriptor
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

class JavaParameterizedParametersStaticCollectionInspectionTest : JvmInspectionTestBase() {
  override val inspection = ParameterizedParametersStaticCollectionInspection()
  override fun getProjectDescriptor(): LightProjectDescriptor =
    JUnitProjectDescriptor(LanguageLevel.HIGHEST, JUnitLibrary.JUNIT4, JUnitLibrary.JUNIT5)

  fun testCreateMethod() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
      public class <warning descr="Parameterized test class 'CreateMethod' lacks data provider method annotated with '@Parameters'"><caret>CreateMethod</warning> { }
    """.trimIndent())
  }

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