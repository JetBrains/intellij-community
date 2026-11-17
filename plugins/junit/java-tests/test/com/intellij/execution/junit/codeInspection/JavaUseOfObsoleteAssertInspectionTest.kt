// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.JUnitProjectDescriptor
import com.intellij.junit.testFramework.MavenTestLib.JUNIT3
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private val descriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST, JUNIT3)

@TestApplication
class JavaUseOfObsoleteAssertInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(descriptor)
  }

  private val myFixture by jvmInspectionFixture(projectFixture) { UseOfObsoleteAssertInspection() }

  @Test
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
