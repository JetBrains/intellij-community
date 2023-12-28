// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.addJUnit3Library
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

class JavaUseOfObsoleteAssertInspectionTest : JvmInspectionTestBase() {
  override val inspection = UseOfObsoleteAssertInspection()

  private class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit3Library()
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST)

  fun testObsoleteAssert() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class ObsoleteAssert {
        public void testMe(int s) {
          junit.framework.Assert.<warning descr="Call to 'assertEquals()' from 'junit.framework.Assert' should be replaced with call to method from 'org.junit.Assert'">assertEquals</warning>("asdfasd", -1, s);
          junit.framework.TestCase.<warning descr="Call to 'assertEquals()' from 'junit.framework.TestCase' should be replaced with call to method from 'org.junit.Assert'">assertEquals</warning>("asdfasd", -1, s);
        }
      }
    """.trimIndent())
  }
}
