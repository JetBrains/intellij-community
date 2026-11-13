// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * The fixture of the [IncorrectCancellationExceptionHandlingInspection] tests.
 * Create it with [incorrectCancellationExceptionHandlingFixture].
 *
 * The project has the stubs of the cancellation exceptions, `ProgressManager` and `Logger`.
 */
class IncorrectCancellationExceptionHandlingFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  /**
   * The extension of the test data files.
   */
  val fileExtension: String,
) {
  /**
   * Checks the highlighting of the test data file `<test name>.<file extension>`.
   */
  fun doTest() {
    fixture.testHighlighting(fixture.getTestName(false) + '.' + fileExtension)
  }
}

/**
 * Creates an [IncorrectCancellationExceptionHandlingFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun incorrectCancellationExceptionHandlingFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<IncorrectCancellationExceptionHandlingFixture> = testFixture("incorrectCancellationExceptionHandlingFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, basePath).init()
  fixture.enableInspections(IncorrectCancellationExceptionHandlingInspection())
  fixture.addClass("""
      package com.intellij.openapi.diagnostic;
      public interface ControlFlowException {}
      """.trimIndent())
  fixture.addClass("""
      package java.util.concurrent;
      public class CancellationException extends IllegalStateException {}
  """.trimIndent())
  fixture.addClass("""
      package com.intellij.openapi.progress;
      import java.util.concurrent.CancellationException;
      public class CeProcessCanceledException extends ProcessCanceledException {
        public CeProcessCanceledException(CancellationException e) {}
      }
      """.trimIndent())
  fixture.addClass("""
      package com.intellij.openapi.progress;
      import java.util.concurrent.CancellationException;
      import com.intellij.openapi.diagnostic.ControlFlowException;
      public class ProcessCanceledException extends CancellationException implements ControlFlowException {}
      """.trimIndent())
  // No 'throws' clause: the inspection must recognize the check by its declaring package, as for the Kotlin
  // 'suspend fun checkCanceled()', which declares nothing.
  fixture.addClass("""
      package com.intellij.openapi.progress;
      public final class ProgressManager {
        public static void checkCanceled() {}
      }
      """.trimIndent())
  fixture.addClass("""
      package com.intellij.openapi.diagnostic;
      public abstract class Logger {
        public static Logger getInstance(Class<?> cl) {return null;}
        public static Logger getInstance(String category) {return null;}
        public void info(Throwable t) {}
        public abstract void info(String message);
        public abstract void info(String message, Throwable t);
        public void warn(Throwable t) {}
        public void error(String message) {}
        public void error(String message, Throwable t) {}
        public void error(Throwable t) {}
        public void debug(Throwable t) {}
        public void debug(String message, Throwable t) {}
        public void warnWithDebug(Throwable t) {}
        public void warnWithDebug(String message, Throwable t) {}
      }
      """.trimIndent())
  initialized(IncorrectCancellationExceptionHandlingFixture(fixture, fileExtension)) {}
}
