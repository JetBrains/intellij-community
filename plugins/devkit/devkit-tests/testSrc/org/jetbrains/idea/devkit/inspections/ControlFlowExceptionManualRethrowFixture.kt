// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.idea.devkit.DevKitBundle

/**
 * The fixture of the [ControlFlowExceptionManualRethrowInspection] tests. Create it with [controlFlowExceptionManualRethrowFixture].
 *
 * The project has the stubs of `ControlFlowException`, `ProcessCanceledException` and `Logger.shouldRethrow`.
 */
class ControlFlowExceptionManualRethrowFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val fileExtension: String,
) {
  /**
   * The name of the quick fix of the inspection.
   */
  val fixName: String get() = DevKitBundle.message("inspections.control.flow.exception.manual.rethrow.fix.name")

  /**
   * Checks the highlighting of `<test name>.<file extension>`, applies the quick fix and checks `<test name>_after.<file extension>`.
   * Call it from any thread. It applies the quick fix on the EDT.
   */
  fun doFixTest() {
    val name = fixture.getTestName(false)
    fixture.testHighlighting("$name.$fileExtension")
    runInEdtAndWait {
      fixture.launchAction(fixture.findSingleIntention(fixName))
      fixture.checkResultByFile("${name}_after.$fileExtension")
    }
  }
}

/**
 * Creates a [ControlFlowExceptionManualRethrowFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun controlFlowExceptionManualRethrowFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<ControlFlowExceptionManualRethrowFixture> = testFixture("controlFlowExceptionManualRethrowFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, basePath).init()
  fixture.enableInspections(ControlFlowExceptionManualRethrowInspection())
  fixture.addClass("package com.intellij.openapi.diagnostic; public interface ControlFlowException {}")
  fixture.addClass("package java.util.concurrent; public class CancellationException extends IllegalStateException {}")
  fixture.addClass("""
      package com.intellij.openapi.progress;
      import java.util.concurrent.CancellationException;
      import com.intellij.openapi.diagnostic.ControlFlowException;
      public class ProcessCanceledException extends CancellationException implements ControlFlowException {}
      """.trimIndent())
  fixture.addClass("""
      package com.intellij.openapi.diagnostic;
      public final class Logger {
        public static boolean shouldRethrow(Throwable t) { return false; }
      }
      """.trimIndent())
  initialized(ControlFlowExceptionManualRethrowFixture(fixture, fileExtension)) {}
}
