// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.inspections.IncorrectProcessCanceledExceptionHandlingInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/incorrectPceHandling")
class KtIncorrectProcessCanceledExceptionHandlingInspectionTest : IncorrectProcessCanceledExceptionHandlingInspectionTestBase() {

  override fun setUp() {
    super.setUp()
    addKotlinFile("JvmPlatformAnnotations.kt",  """
        package kotlin.jvm
        import kotlin.reflect.KClass
        
        @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.CONSTRUCTOR)
        @Retention(AnnotationRetention.SOURCE)
        annotation class Throws(vararg val exceptionClasses: KClass<out Throwable>)
      """)
    addKotlinFile("Throws.kt",  """
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

  private fun addKotlinFile(relativePath: String, @Language("kotlin") fileText: String) {
    myFixture.addFileToProject(relativePath, fileText)
  }

  fun testIncorrectPceHandlingTests() {
    doTest()
  }

  fun testIncorrectPceHandlingWhenMultipleCatchClausesTests() {
    doTest()
  }

  fun testIncorrectPceHandlingWhenPceCaughtImplicitlyTests() {
    doTest()
  }

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/incorrectPceHandling"

  override fun getFileExtension() = "kt"

}
