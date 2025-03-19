// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.addJUnit4Library
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

class JavaExpectedExceptionNeverThrownInspectionTest : JvmInspectionTestBase() {
  override val inspection = ExpectedExceptionNeverThrownInspection()

  private class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit4Library()
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST)

  fun testSimple() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class X {
        @org.junit.Test(expected=<warning descr="Expected 'java.io.IOException' never thrown in body of 'test()'">java.io.IOException</warning>.class)
        public void test() { }
      }
    """.trimIndent())
  }

  fun testInheritance() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class X {
        @org.junit.Test(expected=java.io.EOFException.class)
        public void test() throws Exception {
          foo();
        }
        
        void foo() throws java.io.IOException { }
      }
    """.trimIndent())
  }

  fun testError() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class X {
        @org.junit.Test(expected = Error.class)
        public void test() { }
      }
    """.trimIndent())
  }

  fun testRuntimeException() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class X {
        @org.junit.Test(expected = IllegalArgumentException.class)
        public void test() {}
      }
    """.trimIndent())
  }
}
