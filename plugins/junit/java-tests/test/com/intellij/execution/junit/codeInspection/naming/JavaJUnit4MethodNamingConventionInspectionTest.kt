// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection.naming

import com.intellij.junit.testFramework.addJUnit4Library
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.siyeh.ig.naming.NewMethodNamingConventionInspection

class JavaJUnit4MethodNamingConventionInspectionTest : JvmInspectionTestBase() {
  override val inspection by lazy {
    NewMethodNamingConventionInspection().apply {
      setEnabled(true, JUnit4MethodNamingConvention().shortName)
    }
  }

  private class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit4Library()
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST)

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