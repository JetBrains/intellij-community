// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.kotlin.withKotlinStdlib
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertNotNull as assertNotNullK
import kotlin.test.assertNull as assertNullK

@RunWith(JUnit4::class)
class ForbiddenInSuspectContextMethodInspectionTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = PROJECT_DESCRIPTOR_WITH_KOTLIN

  @Before
  fun initInspection() {
    myFixture.enableInspections(ForbiddenInSuspectContextMethodInspection())

    myFixture.addClass("""
      package com.intellij.util.concurrency.annotations;
      
      
      public @interface RequiresBlockingContext {}
    """.trimIndent())
  }

  private val progressManagerDescr = "Do not call 'ProgressManager.checkCanceled' in suspend context. Use top-level 'checkCanceled' function"
  private val progressManagerFix = "Replace 'ProgressManager.checkCanceled' with coroutine-friendly 'checkCanceled'"

  @Test
  fun `progress manager checkCanceled in suspend function`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.ProgressManager
      
      suspend fun myFun() {
        ProgressManager.<warning descr="$progressManagerDescr">checkCa<caret>nceled</warning>()
      }
    """.trimIndent())
    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(progressManagerFix)
    assertNotNullK(intention)
    myFixture.checkPreviewAndLaunchAction(intention)

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.ProgressManager
      import com.intellij.openapi.progress.checkCanceled
      
      suspend fun myFun() {
        checkCanceled()
      }
    """.trimIndent())
  }

  @Test
  fun `progress manager checkCanceled imported in suspend function`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.ProgressManager.checkCanceled
      
      suspend fun myFun() {
        <warning descr="$progressManagerDescr">chec<caret>kCanceled</warning>()
      }
    """.trimIndent())
    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(progressManagerFix)
    assertNotNullK(intention)
    myFixture.checkPreviewAndLaunchAction(intention)

    myFixture.checkResult("""
      import com.intellij.openapi.progress.ProgressManager.checkCanceled
      
      suspend fun myFun() {
          com.intellij.openapi.progress.checkCanceled()
      }
    """.trimIndent())
  }

  @Test
  fun `progress manager checkCanceled in lambda in suspend function`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.ProgressManager
      
      @Suppress("UNUSED_VARIABLE")
      suspend fun myFun() {
        val a: () -> Unit = {
          ProgressManager.checkC<caret>anceled()
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(progressManagerFix)
    assertNullK(intention)
  }

  @Test
  fun `progress manager checkCanceled in suspend lambda`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      @file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
      import com.intellij.openapi.progress.ProgressManager
      
      fun callSuspendFunction(function: suspend () -> Unit) {
      }
      
      fun myFun() {
        callSuspendFunction {
          ProgressManager.<warning descr="$progressManagerDescr">checkCa<caret>nceled</warning>() 
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(progressManagerFix)
    assertNotNullK(intention)
    myFixture.checkPreviewAndLaunchAction(intention)

    myFixture.configureByText("file.kt", """
      @file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
      import com.intellij.openapi.progress.ProgressManager
      import com.intellij.openapi.progress.checkCanceled
      
      fun callSuspendFunction(function: suspend () -> Unit) {
      }
      
      fun myFun() {
        callSuspendFunction {
          checkCanceled() 
        }
      }
    """.trimIndent())
  }

  @Test
  fun `progress checkCanceled in lambda in suspend lambda`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      @file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
      import com.intellij.openapi.progress.ProgressManager
      
      fun callSuspendFunction(function: suspend () -> Unit) {
      }
      
      fun myFun() {
        callSuspendFunction {
          val lambda: () -> Unit = {
            ProgressManager.checkCa<caret>nceled()  
          }
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(progressManagerFix)
    assertNullK(intention)
  }

  @Test
  fun `progress manager checkCanceled in suspend inner function`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      @file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
      import com.intellij.openapi.progress.ProgressManager
      
      fun myFun() {
        suspend fun myInnerFun() {
          ProgressManager.<warning descr="$progressManagerDescr">chec<caret>kCanceled</warning>()
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(progressManagerFix)
    assertNotNullK(intention)
    myFixture.checkPreviewAndLaunchAction(intention)

    myFixture.checkResult("""
      @file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
      import com.intellij.openapi.progress.ProgressManager
      import com.intellij.openapi.progress.checkCanceled
      
      fun myFun() {
        suspend fun myInnerFun() {
          checkCanceled()
        }
      }
    """.trimIndent())
  }

  @Test
  fun `progress manager checkCanceled in inner suspend lambda`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      @file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
      import com.intellij.openapi.progress.ProgressManager
      
      fun callSuspendFunction(function: suspend () -> Unit) {
      }
      
      val myLambda: () -> Unit = {
        callSuspendFunction {
          ProgressManager.<warning descr="$progressManagerDescr">chec<caret>kCanceled</warning>()
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(progressManagerFix)
    assertNotNullK(intention)
    myFixture.checkPreviewAndLaunchAction(intention)

    myFixture.checkResult("""
      @file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
      import com.intellij.openapi.progress.ProgressManager
      import com.intellij.openapi.progress.checkCanceled
      
      fun callSuspendFunction(function: suspend () -> Unit) {
      }
      
      val myLambda: () -> Unit = {
        callSuspendFunction {
          checkCanceled()
        }
      }
    """.trimIndent())
  }

  @Test
  fun `progress manager no warning in non-suspend context`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      @file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
      import com.intellij.openapi.progress.ProgressManager
      
      fun myFun() {
        ProgressManager.checkCanceled()
        
        val l: () -> Unit = {
          ProgressManager.checkCanceled()
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  @Test
  fun `custom marked function`() {
    myFixture.configureByText("file.kt", """
      import com.intellij.util.concurrency.annotations.*
      
      @RequiresBlockingContext
      fun iVeryNeedBlockingContext() {
      }
      
      suspend fun suspendContext() {
        <warning descr="Method 'iVeryNeedBlockingContext' annotated with @RequiresBlockingContext. It is not designed to be called in suspend functions">iVeryN<caret>eedBlockingContext</warning>()
      }
    """.trimIndent())

    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(progressManagerFix)
    assertNullK(intention)
  }

  @Test
  fun `progress manager checkCanceled inside restricted suspension function by class`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import kotlin.coroutines.RestrictsSuspension
      import com.intellij.openapi.progress.ProgressManager
      
      @RestrictsSuspension
      class A {
        suspend fun a() {
          println()
          ProgressManager.checkCanceled()
        }
      }
    """.trimIndent())

    myFixture.testHighlighting()
  }

  @Test
  fun `progress manager checkCanceled inside restricted suspension function by receiver`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import kotlin.coroutines.RestrictsSuspension
      import com.intellij.openapi.progress.ProgressManager
      
      @RestrictsSuspension
      interface A
      
      suspend fun A.restricted() {
        ProgressManager.checkCanceled()
      }
      
      suspend fun A?.restrictedNullable() {
        ProgressManager.checkCanceled()
      }
    """.trimIndent())

    myFixture.testHighlighting()
  }

  @Test
  fun `progress manager checkCanceled inside restricted suspension lambda`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.ProgressManager
      
      fun a() {
        sequence<Int> {
          ProgressManager.checkCanceled()
          yield(3)
        }
      }
    """.trimIndent())

    myFixture.testHighlighting()
  }

  private fun addCheckCanceledFunctions() {
    myFixture.addClass("""
      package com.intellij.openapi.progress;
      
      import com.intellij.util.concurrency.annotations.*;
      
      public class ProgressManager {
      
        @RequiresBlockingContext
        public static void checkCanceled() throws ProcessCanceledException {
        
        }
      }
    """.trimIndent())

    myFixture.configureByText("utils.kt", /*language=kotlin*/ """
      package com.intellij.openapi.progress
      
      @Suppress("RedundantSuspendModifier")
      suspend fun checkCanceled(): Unit = Unit
    """.trimIndent())
  }
}

private val PROJECT_DESCRIPTOR_WITH_KOTLIN = object : DefaultLightProjectDescriptor() {
  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_17
  }
}.withKotlinStdlib()