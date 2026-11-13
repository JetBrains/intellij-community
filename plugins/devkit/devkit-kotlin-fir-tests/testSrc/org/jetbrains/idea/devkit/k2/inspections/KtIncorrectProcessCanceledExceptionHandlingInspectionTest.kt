// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.inspections.IncorrectCancellationExceptionHandlingFixture
import org.jetbrains.idea.devkit.inspections.incorrectCancellationExceptionHandlingFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.test.util.invalidateCaches
import org.junit.jupiter.api.Test

/**
 * Creates an [IncorrectCancellationExceptionHandlingFixture] for one test with Kotlin test files. Declare it as an instance field.
 *
 * The fixture adds the Kotlin runtime to the module and the Kotlin stubs of the test exceptions and of `kotlinx.coroutines`.
 * After the test, it invalidates the Kotlin caches of the project.
 * [basePath] is the test data path under the community directory.
 */
fun ktIncorrectCancellationExceptionHandlingFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/incorrectCeHandling",
): TestFixture<IncorrectCancellationExceptionHandlingFixture> = testFixture("ktIncorrectCancellationExceptionHandlingFixture") {
  val cancellation = incorrectCancellationExceptionHandlingFixture(projectFixture, "kt", basePath).init()
  val fixture = cancellation.fixture
  withContext(Dispatchers.EDT) {
    ConfigLibraryUtil.configureKotlinRuntime(fixture.module)
  }
  fixture.addFileToProject("SomeControlFlowException.kt", """
      package com.example
      import com.intellij.openapi.diagnostic.ControlFlowException
      class SomeControlFlowException : RuntimeException(), ControlFlowException
    """.trimIndent())
  fixture.addFileToProject("SubclassOfProcessCanceledException.kt", """
      package com.example
      import com.intellij.openapi.progress.ProcessCanceledException
      class SubclassOfProcessCanceledException : ProcessCanceledException()
    """.trimIndent())
  fixture.addFileToProject("CancellationException.kt", """
      package kotlinx.coroutines
      @SinceKotlin("1.4")
      typealias CancellationException = java.util.concurrent.CancellationException
    """.trimIndent())
  fixture.addFileToProject("Job.kt", """
      package kotlinx.coroutines
      interface Job
      fun Job.ensureActive() {}
    """.trimIndent())
  initialized(cancellation) {
    withContext(Dispatchers.EDT) {
      fixture.project.invalidateCaches()
    }
  }
}

@TestDataPath("\$CONTENT_ROOT/testData/inspections/incorrectCeHandling")
@TestApplication
class KtIncorrectProcessCanceledExceptionHandlingInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val cancellation by ktIncorrectCancellationExceptionHandlingFixture(projectFixture)

  @Test
  fun testIncorrectCeLoggedTests() {
    cancellation.doTest()
  }

  @Test
  fun testIncorrectPceHandlingTests() {
    cancellation.doTest()
  }

  @Test
  fun testIncorrectPceHandlingWhenMultipleCatchClausesTests() {
    cancellation.doTest()
  }

  @Test
  fun testIncorrectPceHandlingWhenPceCaughtImplicitlyTests() {
    cancellation.doTest()
  }
}


/*
Ignored: Analysis API doesn't work correctly with K1, and tests on Aggregator are run in K1 mode (setting system property doesn't change it)
@TestDataPath("/inspections/incorrectCeHandling")
class KtIncorrectCancellationExceptionHandlingInspectionTest : KtIncorrectCancellationExceptionHandlingInspectionTestBase() {

  private val USE_K1_KEY = "idea.kotlin.plugin.use.k1.obsolete"
  private var previousK1Property: String? = null

  override fun setUp() {
    previousK1Property = System.getProperty(USE_K1_KEY)
    System.setProperty(USE_K1_KEY, "false")
    super.setUp()
    myFixture.addClass("""
      package java.util.concurrent;
      public class CancellationException extends IllegalStateException {
        public CancellationException() {}
        public CancellationException(String message) { super(message); }
      }
    """.trimIndent()
    )
    addKotlinFile("CancellationException.kt", """
        package kotlinx.coroutines

        @SinceKotlin("1.4")
        typealias CancellationException = java.util.concurrent.CancellationException
      """)
    addKotlinFile("SubclassOfCancellationException.kt", """
        package com.example
        import kotlinx.coroutines.CancellationException
        class SubclassOfCancellationException : CancellationException()
      """)
  }

  override fun tearDown() {
    try {
      val prevK2Property = previousK1Property
      if (prevK2Property != null) {
        System.setProperty(USE_K1_KEY, prevK2Property)
      }
      else {
        System.clearProperty(USE_K1_KEY)
      }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testIncorrectCeHandlingTests() {
    doTest()
  }

  fun testIncorrectCeHandlingInSuspendingLambdasTests() {
    doTest()
  }

  fun testIncorrectCeHandlingWhenMultipleCatchClausesTests() {
    doTest()
  }

  // TODO: disabled - for some reason @Throws cannot be resolved in test data
  /*fun testIncorrectCeHandlingWhenCeCaughtImplicitlyTests() {
    doTest()
  }*/

}
*/
