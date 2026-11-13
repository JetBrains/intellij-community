// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

/**
 * A fixture without `com.intellij.diagnostic.ControlFlowExceptionsKt`, as a plugin built against a platform before 263.
 */
@TestApplication
class ControlFlowExceptionManualRethrowWithoutHelperInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val controlFlow by controlFlowExceptionManualRethrowFixture(projectFixture, "java")
  private val myFixture get() = controlFlow.fixture

  @Test
  fun testGuardNotReported() {
    myFixture.configureByText("GuardWithoutHelper.java", """
      import com.intellij.openapi.diagnostic.ControlFlowException;

      class GuardWithoutHelper {
        void m(Throwable e) throws Throwable {
          if (e instanceof ControlFlowException) throw e;
        }
      }
      """.trimIndent())
    myFixture.checkHighlighting()
  }
}
