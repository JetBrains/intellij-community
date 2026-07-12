// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.DevKitBundle

abstract class ControlFlowExceptionManualRethrowInspectionTestBase : LightJavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ControlFlowExceptionManualRethrowInspection())
    myFixture.addClass("package com.intellij.openapi.diagnostic; public interface ControlFlowException {}")
    myFixture.addClass("package java.util.concurrent; public class CancellationException extends IllegalStateException {}")
    myFixture.addClass("""
        package com.intellij.openapi.progress;
        import java.util.concurrent.CancellationException;
        import com.intellij.openapi.diagnostic.ControlFlowException;
        public class ProcessCanceledException extends CancellationException implements ControlFlowException {}
        """.trimIndent())
    myFixture.addClass("""
        package com.intellij.openapi.diagnostic;
        public final class Logger {
          public static boolean shouldRethrow(Throwable t) { return false; }
        }
        """.trimIndent())
  }

  protected abstract fun getFileExtension(): String

  protected val fixName: String get() = DevKitBundle.message("inspections.control.flow.exception.manual.rethrow.fix.name")

  protected fun doFixTest() {
    val name = getTestName(false)
    myFixture.testHighlighting("$name.${getFileExtension()}")
    myFixture.launchAction(myFixture.findSingleIntention(fixName))
    myFixture.checkResultByFile("${name}_after.${getFileExtension()}")
  }
}
