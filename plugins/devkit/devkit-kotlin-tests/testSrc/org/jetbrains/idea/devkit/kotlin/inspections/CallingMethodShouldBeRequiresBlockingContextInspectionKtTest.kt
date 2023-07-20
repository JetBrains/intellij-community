// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import org.jetbrains.idea.devkit.inspections.CallingMethodShouldBeRequiresBlockingContextInspection
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertNotNull as assertNotNullK

@RunWith(JUnit4::class)
class CallingMethodShouldBeRequiresBlockingContextInspectionKtTest : KtBlockingContextInspectionTestCase() {
  @Before
  fun enableInspection() {
    myFixture.enableInspections(CallingMethodShouldBeRequiresBlockingContextInspection::class.java)
  }

  private val inspectionDescr = "Calling method should be annotated with '@RequiresBlockingContext'"
  private val inspectionFix = "Annotate calling method with '@RequiresBlockingContext'"

  @Test
  fun `calling checkCanceled`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.*
      
      fun a(b: Boolean, c: Boolean) {
        if (b) {
          if (c) {
            ProgressManager.<weak_warning descr="$inspectionDescr">checkC<caret>anceled</weak_warning>()
          }
        }
      }
    """.trimIndent())

    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(inspectionFix)
    assertNotNullK(intention)
    myFixture.checkPreviewAndLaunchAction(intention)

    myFixture.checkResult("""
      import com.intellij.openapi.progress.*
      import com.intellij.util.concurrency.annotations.RequiresBlockingContext
      
      @RequiresBlockingContext
      fun a(b: Boolean, c: Boolean) {
        if (b) {
          if (c) {
            ProgressManager.checkCanceled()
          }
        }
      }
    """.trimIndent())
  }

  @Test
  fun `inspection in parameter`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.util.concurrency.annotations.RequiresBlockingContext
      
      @RequiresBlockingContext
      fun a(): Int {
        return 10
      }
      
      fun b(a: Int) {
        println(a)
      }
      
      fun c() {
        b(<weak_warning descr="$inspectionDescr"><caret>a</weak_warning>())
      }
    """.trimIndent())

    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(inspectionFix)
    assertNotNullK(intention)
    myFixture.checkPreviewAndLaunchAction(intention)

    myFixture.checkResult("""
      import com.intellij.util.concurrency.annotations.RequiresBlockingContext
      
      @RequiresBlockingContext
      fun a(): Int {
        return 10
      }
      
      fun b(a: Int) {
        println(a)
      }
      
      @RequiresBlockingContext
      fun c() {
        b(a())
      }
    """.trimIndent())
  }

  @Test
  fun `calling checkCanceled in let`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.*
      
      fun a(b: Any?) {
        b?.let {
          ProgressManager.<weak_warning descr="$inspectionDescr">checkCance<caret>led</weak_warning>()
        }
      }
    """.trimIndent())

    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(inspectionFix)
    assertNotNullK(intention)
    myFixture.checkPreviewAndLaunchAction(intention)

    myFixture.checkResult("""
      import com.intellij.openapi.progress.*
      import com.intellij.util.concurrency.annotations.RequiresBlockingContext
      
      @RequiresBlockingContext
      fun a(b: Any?) {
        b?.let {
          ProgressManager.checkCanceled()
        }
      }
    """.trimIndent())
  }

  @Test
  fun `no inspection if parent function is annotated`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.util.concurrency.annotations.RequiresBlockingContext
      import com.intellij.openapi.progress.*
      
      interface A {
        @RequiresBlockingContext
        fun a()
      }
      
      class B : A {
        override fun a() {
          ProgressManager.checkCanceled()
        }
      }
    """.trimIndent())

    myFixture.testHighlighting()
  }

  @Test
  fun `no inspection in suspend`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.*
      
      suspend fun a() {
        ProgressManager.checkCanceled()
      }
    """.trimIndent())

    myFixture.testHighlighting()
  }

  @Test
  fun `no inspection in annotated`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.*
      import com.intellij.util.concurrency.annotations.RequiresBlockingContext
      
      @RequiresBlockingContext
      fun a() {
        ProgressManager.checkCanceled()
      }
    """.trimIndent())

    myFixture.testHighlighting()
  }

  @Test
  fun `no inspection in lambda`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.*
           
      fun a() {
        sequenceOf(1, 2, 3)
          .filter {
            ProgressManager.checkCanceled()
            true
          }
      }
    """.trimIndent())

    myFixture.testHighlighting()
  }
}