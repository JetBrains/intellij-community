// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalInspectionTool
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
    @Suppress("UNCHECKED_CAST")
    myFixture.enableInspections(
      Class.forName("org.jetbrains.idea.devkit.kotlin.inspections.ForbiddenInSuspectContextMethodInspection") as Class<LocalInspectionTool>
    )

    myFixture.addClass("""
      package com.intellij.util.concurrency.annotations;
      
      
      public @interface RequiresBlockingContext {}
    """.trimIndent())
  }

  private val progressManagerDescr = "Do not call 'ProgressManager.checkCanceled' in suspend context. Use top-level 'checkCancelled' function"
  private val progressManagerFix = "Replace 'ProgressManager.checkCanceled' with coroutine-friendly 'checkCancelled'"

  private val invokeAndWaitDescr = "'invokeAndWait' can block current coroutine. Use 'Dispatchers.EDT' instead"
  private val invokeAndWaitFix = "Replace 'invokeAndWait' call with 'withContext(Dispatchers.EDT) {}'"

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
      import com.intellij.openapi.progress.checkCancelled
      
      suspend fun myFun() {
        checkCancelled()
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
      import com.intellij.openapi.progress.checkCancelled
      
      fun myFun() {
        suspend fun myInnerFun() {
          checkCancelled()
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
      import com.intellij.openapi.progress.checkCancelled
      
      fun callSuspendFunction(function: suspend () -> Unit) {
      }
      
      val myLambda: () -> Unit = {
        callSuspendFunction {
          checkCancelled()
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

  @Test
  fun `progress manager checkCanceled inside runBlockingCancellable`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.*
      import com.intellij.util.concurrency.annotations.*
      
      @RequiresBlockingContext
      fun blockingFun() {
        runBlockingCancellable {
          ProgressManager.<warning descr="$progressManagerDescr">checkC<caret>anceled</warning>()
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(progressManagerFix)
    assertNotNullK(intention)
    myFixture.checkPreviewAndLaunchAction(intention)

    myFixture.checkResult("""
      import com.intellij.openapi.progress.*
      import com.intellij.util.concurrency.annotations.*
      
      @RequiresBlockingContext
      fun blockingFun() {
        runBlockingCancellable {
          checkCancelled()
        }
      }
    """.trimIndent())
  }

  @Test
  fun `progress manager checkCanceled inside inline lambda`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.*
      
      suspend fun process(items: List<Int>) {
        items.map {
          ProgressManager.<warning descr="$progressManagerDescr">checkC<caret>anceled</warning>()
          it + 1
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(progressManagerFix)
    assertNotNullK(intention)
    myFixture.checkPreviewAndLaunchAction(intention)

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.*
      
      suspend fun process(items: List<Int>) {
        items.map {
          checkCancelled()
          it + 1
        }
      }
    """.trimIndent())
  }

  @Test
  fun `progress manager checkCanceled inside crossinline lambda`() {
    addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      @file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
      import com.intellij.openapi.progress.*
      
      inline fun a(crossinline l: () -> Unit) {
        
      }
      
      suspend fun process() {
        a {
          ProgressManager.checkC<caret>anceled()
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  @Test
  fun `invokeAndWait with lambda argument`() {
    addApplicationAndEtc()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.application.*
      
      suspend fun a() {
        ApplicationManager.getApplication().<warning descr="$invokeAndWaitDescr">invokeAnd<caret>Wait</warning> {
          println()
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(invokeAndWaitFix)
    assertNotNullK(intention)
    myFixture.checkPreviewAndLaunchAction(intention)

    myFixture.checkResult("""
      import com.intellij.openapi.application.*
      import kotlinx.coroutines.Dispatchers
      import kotlinx.coroutines.withContext
      
      suspend fun a() {
        withContext(Dispatchers.EDT) {
          println()
        }
      }
    """.trimIndent())
  }

  @Test
  fun `invokeAndWait with modality argument`() {
    addApplicationAndEtc()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.application.*
      
      suspend fun a() {
        ApplicationManager.getApplication().<warning descr="$invokeAndWaitDescr">invokeAnd<caret>Wait</warning>({
          println()
        }, null)
      }
    """.trimIndent())
    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(invokeAndWaitFix)
    assertNotNullK(intention)
    myFixture.checkPreviewAndLaunchAction(intention)

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.application.*
      import kotlinx.coroutines.Dispatchers
      import kotlinx.coroutines.withContext
      
      suspend fun a() {
        withContext(Dispatchers.EDT) {
          println()
        }
      }
    """.trimIndent())
  }

  @Test
  fun `invokeAndWait on Application receiver`() {
    addApplicationAndEtc()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.application.*
      
      suspend fun Application.a() {
        <warning descr="$invokeAndWaitDescr">invokeAnd<caret>Wait</warning> {
          println()
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(invokeAndWaitFix)
    assertNotNullK(intention)
    myFixture.checkPreviewAndLaunchAction(intention)

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.application.*
      import kotlinx.coroutines.Dispatchers
      import kotlinx.coroutines.withContext
      
      suspend fun Application.a() {
        withContext(Dispatchers.EDT) {
          println()
        }
      }
    """.trimIndent())
  }

  private fun addApplicationAndEtc() {
    myFixture.addClass("""
        package com.intellij.openapi.progress;
        
        public class ProcessCanceledException extends RuntimeException {
          public ProcessCanceledException() { }
        }
      """.trimIndent())

    myFixture.addClass("""
        package com.intellij.openapi.application;
        
        import com.intellij.util.concurrency.annotations.RequiresBlockingContext;
        import com.intellij.openapi.progress.ProcessCanceledException;
        
        public interface Application {
            @RequiresBlockingContext
            void invokeAndWait(Runnable runnable, ModalityState modalityState) throws ProcessCanceledException;
            
            @RequiresBlockingContext
            void invokeAndWait(Runnable runnable) throws ProcessCanceledException;
        }
      """.trimIndent())

    myFixture.addClass("""
        package com.intellij.openapi.application;
        
        public class ApplicationManager {
          public static Application getApplication() {
            return null;
          }
        }
      """.trimIndent())

    myFixture.addClass("""
        package com.intellij.openapi.application;
        
        public abstract class ModalityState {
          
        }
      """.trimIndent())
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
      import kotlinx.coroutines.*

      @Suppress("RedundantSuspendModifier")
      suspend fun checkCancelled(): Unit = Unit
      
      fun <T> runBlockingCancellable(action: suspend CoroutineScope.() -> T): T {
        throw RuntimeException("Unimplemented")
      }
    """.trimIndent())
  }
}

private val PROJECT_DESCRIPTOR_WITH_KOTLIN = object : DefaultLightProjectDescriptor() {
  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_17
  }
}.withKotlinStdlib()
  .withRepositoryLibrary("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")