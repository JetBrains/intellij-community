// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.JUnitLibrary
import com.intellij.junit.testFramework.JUnitProjectDescriptor
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

class JavaJUnit3StyleTestMethodInJUnit4ClassInspectionTest : JvmInspectionTestBase() {
  override val inspection = JUnit3StyleTestMethodInJUnit4ClassInspection()
  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST,
                                                                                       JUnitLibrary.JUNIT4, JUnitLibrary.JUNIT5)

  fun testJUnit3StyleTestMethodInJUnit4Class() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.Test;

      public class JUnit3StyleTestMethodInJUnit4Class {
        @Test
        public void junit4Test() { }

        public void <warning descr="Old style JUnit test method 'testJUnit3()' in JUnit 4 class">testJUnit3</warning>() { }
      }
    """.trimIndent())
  }

  fun testBeforeAnnotationUsed() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.Before;

      public class BeforeAnnotationUsed {
        @Before
        public void before() { }

        public void <warning descr="Old style JUnit test method 'testOldStyle()' in JUnit 4 class">testOldStyle</warning>() { }
      }
    """.trimIndent())
  }

  fun testSimpleJUnit5() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.jupiter.api.Test;

      public class SimpleJUnit5 {
        @Test
        public void testIt() { }
      }
    """.trimIndent())
  }

  fun testOtherAnnotation() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.*;

      public class OtherAnnotation {
        @Test
        public void testFoo() { }
        
        public void <warning descr="Old style JUnit test method 'testSmth()' in JUnit 4 class">testSmth</warning>() { }
        
        @Ignore
        public void testIgnored() { }
        
        @After
        public void testAfter() { }
        
        @Before
        public void testBefore() { }
      }
    """.trimIndent())
  }
}
