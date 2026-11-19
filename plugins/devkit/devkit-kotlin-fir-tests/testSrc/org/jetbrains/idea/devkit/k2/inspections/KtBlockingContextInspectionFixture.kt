// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * The fixture of the Kotlin tests of the blocking context inspections. Create it with [ktBlockingContextInspectionFixture].
 *
 * The project has the `RequiresBlockingContext` annotation. Use the project of [PROJECT_DESCRIPTOR_WITH_KOTLIN].
 */
class KtBlockingContextInspectionFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * Adds the stubs of `ProcessCanceledException`, `Application`, `ApplicationManager`, `ModalityState` and `invokeAndWaitIfNeeded`.
   */
  fun addApplicationAndEtc() {
    fixture.addClass("""
        package com.intellij.openapi.progress;
        
        public class ProcessCanceledException extends RuntimeException {
          public ProcessCanceledException() { }
        }
      """.trimIndent())

    fixture.addClass("""
        package com.intellij.openapi.application;
        
        import com.intellij.util.concurrency.annotations.RequiresBlockingContext;
        import com.intellij.openapi.progress.ProcessCanceledException;
        
        public interface Application {
            @RequiresBlockingContext
            void invokeAndWait(Runnable runnable, ModalityState modalityState) throws ProcessCanceledException;
            
            @RequiresBlockingContext
            void invokeAndWait(Runnable runnable) throws ProcessCanceledException;
            
            @RequiresBlockingContext
            ModalityState getDefaultModalityState();
            
            @RequiresBlockingContext
            void invokeLater(Runnable runnable);
        }
      """.trimIndent())

    fixture.addClass("""
        package com.intellij.openapi.application;
        
        public class ApplicationManager {
          public static Application getApplication() {
            return null;
          }
        }
      """.trimIndent())

    fixture.addClass("""
        package com.intellij.openapi.application;
        
        import com.intellij.util.concurrency.annotations.RequiresBlockingContext;
        
        public abstract class ModalityState {
          @RequiresBlockingContext
          public static ModalityState defaultModalityState() {
            return null;
          }
          
          public static ModalityState any() {
            return null;
          }
        }
      """.trimIndent())

    fixture.configureByText("utils.kt", """
      package com.intellij.openapi.application
      
      import com.intellij.util.concurrency.annotations.RequiresBlockingContext
      
      @RequiresBlockingContext
      fun <T> invokeAndWaitIfNeeded(modalityState: ModalityState? = null, runnable: () -> T): T {
      }
    """.trimIndent())
  }
  /**
   * Adds the stubs of `ProgressManager.checkCanceled`, the suspend `checkCanceled` and `runBlockingCancellable`.
   */
  fun addCheckCanceledFunctions() {
    fixture.addClass("""
      package com.intellij.openapi.progress;
      
      import com.intellij.util.concurrency.annotations.*;
      
      public class ProgressManager {
      
        @RequiresBlockingContext
        public static void checkCanceled() throws ProcessCanceledException {
        
        }
      }
    """.trimIndent())

    fixture.configureByText("utils.kt", /*language=kotlin*/ """
      package com.intellij.openapi.progress
      import kotlinx.coroutines.*

      @Suppress("RedundantSuspendModifier")
      suspend fun checkCanceled(): Unit = Unit
      
      fun <T> runBlockingCancellable(action: suspend CoroutineScope.() -> T): T {
        throw RuntimeException("Unimplemented")
      }
    """.trimIndent())
  }
}

/**
 * Creates a [KtBlockingContextInspectionFixture] for one test. Declare it as an instance field.
 *
 * The fixture adds the `RequiresBlockingContext` annotation and enables [inspection].
 */
fun ktBlockingContextInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  inspection: Class<out LocalInspectionTool>,
): TestFixture<KtBlockingContextInspectionFixture> = testFixture("ktBlockingContextInspectionFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  fixture.addFileToProject("RequiresBlockingContext.kt", """
    package com.intellij.util.concurrency.annotations;

    @MustBeDocumented
    @Retention(AnnotationRetention.BINARY)
    @Target(
      AnnotationTarget.FUNCTION,
      AnnotationTarget.PROPERTY_GETTER,
      AnnotationTarget.PROPERTY_SETTER,
    )
    @ApiStatus.Experimental
    annotation class RequiresBlockingContext(
      val replaceWith: ReplaceWith = ReplaceWith(""),
    )
  """.trimIndent())
  fixture.enableInspections(inspection)
  initialized(KtBlockingContextInspectionFixture(fixture)) {}
}
