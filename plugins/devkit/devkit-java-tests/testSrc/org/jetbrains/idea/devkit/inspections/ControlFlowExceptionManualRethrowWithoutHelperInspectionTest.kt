// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

/**
 * A fixture without `com.intellij.diagnostic.ControlFlowExceptionsKt`, as a plugin built against a platform before 263.
 */
class ControlFlowExceptionManualRethrowWithoutHelperInspectionTest : ControlFlowExceptionManualRethrowInspectionTestBase() {

  override fun getFileExtension(): String {
    return "java"
  }

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
