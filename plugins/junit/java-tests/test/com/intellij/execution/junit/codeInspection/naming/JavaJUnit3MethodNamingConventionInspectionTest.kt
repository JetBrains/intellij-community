// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection.naming

import com.intellij.junit.testFramework.addJUnit3Library
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.siyeh.ig.naming.NewMethodNamingConventionInspection

class JavaJUnit3MethodNamingConventionInspectionTest : JvmInspectionTestBase() {
  override val inspection by lazy {
    NewMethodNamingConventionInspection().apply {
      setEnabled(true, JUnit3MethodNamingConvention().shortName)
    }
  }

  private class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit3Library()
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST)

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
