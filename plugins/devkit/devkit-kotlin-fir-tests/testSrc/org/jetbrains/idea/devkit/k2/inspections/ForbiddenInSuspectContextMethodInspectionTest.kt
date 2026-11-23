// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.kotlin.withKotlinStdlib
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.kotlin.inspections.ForbiddenInSuspectContextMethodInspection
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.test.assertNotNull as assertNotNullK
import kotlin.test.assertNull as assertNullK

@TestApplication
class ForbiddenInSuspectContextMethodInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(PROJECT_DESCRIPTOR_WITH_KOTLIN)
  }

  private val blockingContext by ktBlockingContextInspectionFixture(projectFixture, ForbiddenInSuspectContextMethodInspection::class.java)
  private val myFixture get() = blockingContext.fixture

  private val progressManagerDescr = "Do not call 'ProgressManager.checkCanceled' in suspend context. Use top-level 'checkCanceled' function"
  private val progressManagerFix = "Replace 'ProgressManager.checkCanceled' with coroutine-friendly 'checkCanceled'"

  private val invokeAndWaitDescr = "'invokeAndWait' can block current coroutine. Use 'Dispatchers.EDT' instead"
  private val invokeAndWaitFix = "Replace 'invokeAndWait' call with 'withContext(Dispatchers.EDT) {}'"

  private val defaultModalityDescr = "'defaultModalityState()' does not work in suspend context. If it is really necessary, use 'contextModality()'"

  private val navigateToSuspendContextFix = "Navigate to suspend context"

  private val invokeLaterDescr = "'invokeLater' is not idiomatic in suspend context. Use 'Dispatchers.EDT' instead"
  private val invokeLaterFixWithContext = "(May change semantics) Replace 'invokeLater' call with 'withContext(Dispatchers.EDT) {}'"
  private val invokeLaterFixLaunch = "Replace 'invokeLater' with 'launch(Dispatchers.EDT)'"

  @Test
  fun `progress manager checkCanceled in suspend function`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    blockingContext.addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.ProgressManager
      
      suspend fun myFun() {
        ProgressManager.<warning descr="$progressManagerDescr">checkCa<caret>nceled</warning>()
      }
    """.trimIndent())
    myFixture.testHighlighting()

    withContext(Dispatchers.EDT) {
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
  }

  @Test
  fun `progress manager checkCanceled imported in suspend function`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    blockingContext.addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.progress.ProgressManager.checkCanceled
      
      suspend fun myFun() {
        <warning descr="$progressManagerDescr">chec<caret>kCanceled</warning>()
      }
    """.trimIndent())
    myFixture.testHighlighting()

    withContext(Dispatchers.EDT) {
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
  }

  @Test
  fun `progress manager checkCanceled in lambda in suspend function`() {
    blockingContext.addCheckCanceledFunctions()

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
  fun `progress manager checkCanceled in suspend lambda`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    blockingContext.addCheckCanceledFunctions()

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

    withContext(Dispatchers.EDT) {
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
  }

  @Test
  fun `progress checkCanceled in lambda in suspend lambda`() {
    blockingContext.addCheckCanceledFunctions()

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
  fun `progress manager checkCanceled in suspend inner function`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    blockingContext.addCheckCanceledFunctions()

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

    withContext(Dispatchers.EDT) {
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
  }

  @Test
  fun `progress manager checkCanceled in inner suspend lambda`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    blockingContext.addCheckCanceledFunctions()

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

    withContext(Dispatchers.EDT) {
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
  }

  @Test
  fun `progress manager no warning in non-suspend context`() {
    blockingContext.addCheckCanceledFunctions()

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
    RegistryManager.getInstance().get("devkit.inspections.forbidden.method.in.suspend.context")
      .setValue(true, myFixture.testRootDisposable)

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

  private val replaceWithSuspendAlternativeFix = "Replace with suspend alternative"

  @Test
  fun `custom marked function with ReplaceWith`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    RegistryManager.getInstance().get("devkit.inspections.forbidden.method.in.suspend.context")
      .setValue(true, myFixture.testRootDisposable)

    myFixture.configureByText("suspendAlternative.kt", """
      package com.example

      suspend fun suspendAlternative() {
      }
    """.trimIndent())

    myFixture.configureByText("file.kt", """
      import com.intellij.util.concurrency.annotations.*

      @RequiresBlockingContext(ReplaceWith("suspendAlternative()", "com.example.suspendAlternative"))
      fun blockingFunction() {
      }

      suspend fun suspendContext() {
          <warning descr="Method 'blockingFunction' annotated with @RequiresBlockingContext. It is not designed to be called in suspend functions">blocking<caret>Function</warning>()
      }
    """.trimIndent())

    myFixture.testHighlighting()

    withContext(Dispatchers.EDT) {
      val intention = myFixture.getAvailableIntention(replaceWithSuspendAlternativeFix)
      assertNotNullK(intention)
      myFixture.launchAction(intention)

      myFixture.checkResult("""
        import com.example.suspendAlternative
        import com.intellij.util.concurrency.annotations.*

        @RequiresBlockingContext(ReplaceWith("suspendAlternative()", "com.example.suspendAlternative"))
        fun blockingFunction() {
        }

        suspend fun suspendContext() {
            suspendAlternative()
        }
      """.trimIndent())
    }
  }

  @Test
  fun `custom marked function with ReplaceWith without imports`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    RegistryManager.getInstance().get("devkit.inspections.forbidden.method.in.suspend.context")
      .setValue(true, myFixture.testRootDisposable)

    myFixture.configureByText("file.kt", /* language=kotlin */ """
      package demo

      import com.intellij.util.concurrency.annotations.*

      @RequiresBlockingContext(ReplaceWith("suspendAlternative()", imports = []))
      fun blockingFunction() {
      }

      suspend fun suspendAlternative() {
      }

      suspend fun suspendContext() {
          <warning descr="Method 'blockingFunction' annotated with @RequiresBlockingContext. It is not designed to be called in suspend functions">blocking<caret>Function</warning>()
      }
    """.trimIndent())

    myFixture.testHighlighting()

    withContext(Dispatchers.EDT) {
      val intention = myFixture.getAvailableIntention(replaceWithSuspendAlternativeFix)
      assertNotNullK(intention)
      myFixture.launchAction(intention)

      myFixture.checkResult("""
        package demo

        import com.intellij.util.concurrency.annotations.*

        @RequiresBlockingContext(ReplaceWith("suspendAlternative()", imports = []))
        fun blockingFunction() {
        }

        suspend fun suspendAlternative() {
        }

        suspend fun suspendContext() {
            suspendAlternative()
        }
      """.trimIndent())
    }
  }

  @Test
  fun `custom marked function with empty ReplaceWith has no quickfix`() {
    RegistryManager.getInstance().get("devkit.inspections.forbidden.method.in.suspend.context")
      .setValue(true, myFixture.testRootDisposable)

    myFixture.configureByText("file.kt", """
      import com.intellij.util.concurrency.annotations.*

      @RequiresBlockingContext(ReplaceWith(""))
      fun blockingFunction() {
      }

      suspend fun suspendContext() {
        <warning descr="Method 'blockingFunction' annotated with @RequiresBlockingContext. It is not designed to be called in suspend functions">blocking<caret>Function</warning>()
      }
    """.trimIndent())

    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(replaceWithSuspendAlternativeFix)
    assertNullK(intention)
  }

  @Test
  fun `progress manager checkCanceled inside restricted suspension function by class`() {
    blockingContext.addCheckCanceledFunctions()

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
    blockingContext.addCheckCanceledFunctions()

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
    blockingContext.addCheckCanceledFunctions()

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
  fun `progress manager checkCanceled inside runBlockingCancellable`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    blockingContext.addCheckCanceledFunctions()

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

    withContext(Dispatchers.EDT) {
      val intention = myFixture.getAvailableIntention(progressManagerFix)
      assertNotNullK(intention)
      myFixture.checkPreviewAndLaunchAction(intention)

      myFixture.checkResult("""
        import com.intellij.openapi.progress.*
        import com.intellij.util.concurrency.annotations.*
        
        @RequiresBlockingContext
        fun blockingFun() {
          runBlockingCancellable {
              checkCanceled()
          }
        }
      """.trimIndent())
    }
  }

  @Test
  fun `progress manager checkCanceled inside inline lambda`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    blockingContext.addCheckCanceledFunctions()

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

    withContext(Dispatchers.EDT) {
      val intention = myFixture.getAvailableIntention(progressManagerFix)
      assertNotNullK(intention)
      myFixture.checkPreviewAndLaunchAction(intention)

      myFixture.configureByText("file.kt", """
        import com.intellij.openapi.progress.*
        
        suspend fun process(items: List<Int>) {
          items.map {
            checkCanceled()
            it + 1
          }
        }
      """.trimIndent())
    }
  }

  @Test
  fun `progress manager checkCanceled inside crossinline lambda`() {
    blockingContext.addCheckCanceledFunctions()

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
  fun `invokeAndWait with lambda argument`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    blockingContext.addApplicationAndEtc()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.application.*
      
      suspend fun a() {
        ApplicationManager.getApplication().<warning descr="$invokeAndWaitDescr">invokeAnd<caret>Wait</warning> {
          println()
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    withContext(Dispatchers.EDT) {
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
  }

  @Test
  fun `invokeAndWait with modality argument`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    blockingContext.addApplicationAndEtc()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.application.*
      
      suspend fun a() {
        ApplicationManager.getApplication().<warning descr="$invokeAndWaitDescr">invokeAnd<caret>Wait</warning>({
          println()
        }, null)
      }
    """.trimIndent())
    myFixture.testHighlighting()

    withContext(Dispatchers.EDT) {
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
  }

  @Test
  fun `invokeAndWait on Application receiver`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    blockingContext.addApplicationAndEtc()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.application.*
      
      suspend fun Application.a() {
        <warning descr="$invokeAndWaitDescr">invokeAnd<caret>Wait</warning> {
          println()
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    withContext(Dispatchers.EDT) {
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
  }

  @Test
  fun `invokeAndWaitIfNeeded with modality parameter`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    blockingContext.addApplicationAndEtc()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.application.*
      
      suspend fun a() {
        <warning descr="$invokeAndWaitDescr">invokeAn<caret>dWaitIfNeeded</warning>(ModalityState.any()) {
          println()
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    withContext(Dispatchers.EDT) {
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
  }

  @Test
  fun `defaultModalityState in suspend context`() {
    blockingContext.addApplicationAndEtc()

    myFixture.configureByText("file.kt", """
      @file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
      
      import com.intellij.openapi.application.*
      import kotlinx.coroutines.*
      
      class MyService {
        suspend fun fn() {
          val state: ModalityState = ModalityState.<warning descr="$defaultModalityDescr">default<caret>ModalityState</warning>()
        } 
      }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  @Test
  fun `getDefaultModalityState in suspend context`() {
    blockingContext.addApplicationAndEtc()

    myFixture.configureByText("file.kt", """
      @file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
      
      import com.intellij.openapi.application.*
      import kotlinx.coroutines.*
      
      class MyService {
        suspend fun fn() {
          val state: ModalityState = ApplicationManager.getApplication().<warning descr="$defaultModalityDescr">getDefault<caret>ModalityState</warning>()
        } 
      }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  @Test
  fun `check in called non-suspend function`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    blockingContext.addCheckCanceledFunctions()

    myFixture.configureByText("file.kt", """
      @file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
      
      import com.intellij.openapi.progress.*
      
      class MyClass {
        suspend fun a() {
          b()
          b()
          b()
          b()
          b()
        }
         
        fun b() {
          ProgressManager.<warning descr="$progressManagerDescr">check<caret>Canceled</warning>()
        }
      }
      
      fun b() {
        ProgressManager.checkCanceled()
      }
    """.trimIndent())
    myFixture.testHighlighting()

    withContext(Dispatchers.EDT) {
      val intention = myFixture.getAvailableIntention(navigateToSuspendContextFix)
      assertNotNullK(intention)
      myFixture.launchAction(intention)

      myFixture.checkResult("""
        @file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
        
        import com.intellij.openapi.progress.*
        
        class MyClass {
          suspend fun a() {
            <caret>b()
            b()
            b()
            b()
            b()
          }
           
          fun b() {
            ProgressManager.checkCanceled()
          }
        }
        
        fun b() {
          ProgressManager.checkCanceled()
        }
      """.trimIndent())
    }
  }

  @Test
  fun `invokeLater replace withContext`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    blockingContext.addApplicationAndEtc()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.application.*
      
      suspend fun a() {
        ApplicationManager.getApplication().<warning descr="$invokeLaterDescr">inv<caret>okeLater</warning> {
          println()
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    withContext(Dispatchers.EDT) {
      val intention = myFixture.getAvailableIntention(invokeLaterFixWithContext)
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
  }

  @Test
  fun `invokeLater with launch`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    blockingContext.addApplicationAndEtc()

    myFixture.configureByText("file.kt", """
      import com.intellij.openapi.application.*
      import kotlinx.coroutines.*
      
      suspend fun CoroutineScope.a() {
        ApplicationManager.getApplication().<warning descr="$invokeLaterDescr">inv<caret>okeLater</warning> {
          println()
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    withContext(Dispatchers.EDT) {
      val intention = myFixture.getAvailableIntention(invokeLaterFixLaunch)
      assertNotNullK(intention)
      myFixture.checkPreviewAndLaunchAction(intention)

      myFixture.checkResult("""
        import com.intellij.openapi.application.*
        import kotlinx.coroutines.*
        
        suspend fun CoroutineScope.a() {
            launch(Dispatchers.EDT) {
              println()
            }
        }
      """.trimIndent())
    }
  }

  @Test
  fun `call as parameter should be checked`() {
    RegistryManager.getInstance().get("devkit.inspections.forbidden.method.in.suspend.context")
      .setValue(true, myFixture.testRootDisposable)

    myFixture.configureByText("file.kt", """
      import com.intellij.util.concurrency.annotations.*
      
      @RequiresBlockingContext
      fun a(): Int {
        return 10
      }
      
      fun b(a: Int) {
        println(a)
      }
      
      suspend fun c() {
        b(<warning descr="Method 'a' annotated with @RequiresBlockingContext. It is not designed to be called in suspend functions">a</warning>())
      }
    """.trimIndent())

    myFixture.testHighlighting()
  }
}

internal val PROJECT_DESCRIPTOR_WITH_KOTLIN = object : DefaultLightProjectDescriptor() {
  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_17
  }
}.withKotlinStdlib()
  .withRepositoryLibrary("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")