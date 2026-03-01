// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.JUnitLibrary
import com.intellij.junit.testFramework.JUnitProjectDescriptor
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

class JavaUseOfObsoleteAssertInspectionTest : JvmInspectionTestBase() {
  override val inspection = UseOfObsoleteAssertInspection()
  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST, JUnitLibrary.JUNIT3)
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
