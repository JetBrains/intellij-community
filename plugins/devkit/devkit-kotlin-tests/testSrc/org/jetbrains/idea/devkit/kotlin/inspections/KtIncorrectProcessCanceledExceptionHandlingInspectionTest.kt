// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.inspections.IncorrectCancellationExceptionHandlingInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

abstract class KtIncorrectCancellationExceptionHandlingInspectionTestBase : IncorrectCancellationExceptionHandlingInspectionTestBase() {

  override fun setUp() {
    super.setUp()
    addKotlinFile("JvmPlatformAnnotations.kt", """
        package kotlin.jvm
        import kotlin.reflect.KClass
        
        @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.CONSTRUCTOR)
        @Retention(AnnotationRetention.SOURCE)
        annotation class Throws(vararg val exceptionClasses: KClass<out Throwable>)
      """)
    addKotlinFile("Throws.kt", """
        package kotlin
        
        @SinceKotlin("1.4")
        actual typealias Throws = kotlin.jvm.Throws
      """)
    addKotlinFile("SubclassOfProcessCanceledException.kt", """
        package com.example
        import com.intellij.openapi.progress.ProcessCanceledException
        class SubclassOfProcessCanceledException : ProcessCanceledException()
      """)
  }

  protected fun addKotlinFile(relativePath: String, @Language("kotlin") fileText: String) {
    myFixture.addFileToProject(relativePath, fileText)
  }

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/incorrectCeHandling"

  override fun getFileExtension() = "kt"

}

@TestDataPath("\$CONTENT_ROOT/testData/inspections/incorrectCeHandling")
class KtIncorrectProcessCanceledExceptionHandlingInspectionTest : KtIncorrectCancellationExceptionHandlingInspectionTestBase() {

  fun testIncorrectPceHandlingTests() {
    doTest()
  }

  fun testIncorrectPceHandlingWhenMultipleCatchClausesTests() {
    doTest()
  }

  fun testIncorrectPceHandlingWhenPceCaughtImplicitlyTests() {
    doTest()
  }
}


/*
Ignored: Analysis API doesn't work correctly with K1, and tests on Aggregator are run in K1 mode (setting system property doesn't change it)
@TestDataPath("/inspections/incorrectCeHandling")
class KtIncorrectCancellationExceptionHandlingInspectionTest : KtIncorrectCancellationExceptionHandlingInspectionTestBase() {

  private val USE_K2_KEY = "idea.kotlin.plugin.use.k2"
  private var previousK2Property: String? = null

  override fun setUp() {
    previousK2Property = System.getProperty(USE_K2_KEY)
    System.setProperty(USE_K2_KEY, "true")
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
      val prevK2Property = previousK2Property
      if (prevK2Property != null) {
        System.setProperty(USE_K2_KEY, prevK2Property)
      }
      else {
        System.clearProperty(USE_K2_KEY)
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
