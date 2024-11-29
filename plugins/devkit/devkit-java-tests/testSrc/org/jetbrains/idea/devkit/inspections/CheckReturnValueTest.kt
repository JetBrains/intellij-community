// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

class CheckReturnValueTest : LightDevKitInspectionFixTestBase(), ExpectedPluginModeProvider {
  override fun getFileExtension(): String = "kt"

  override fun setUp() {
    super.setUp()
    setUpWithKotlinPlugin(project) { }
    myFixture.enableInspections(CheckReturnValueInspection())
    myFixture.allowTreeAccessForAllFiles()
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = object : DefaultLightProjectDescriptor() {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      MavenDependencyUtil.addFromMaven(model, "org.jetbrains:annotations:24.0.0")
      MavenDependencyUtil.addFromMaven(model, "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    }
  }

  fun testFunctionThatDoesNotCheckFunctionResult() {
    doIt("""
      fun main() {
        <error descr="[checkReturnValue] Return value must be checked">someAction()</error>
      }
    """.trimIndent())
  }

  fun testFunctionThatDoesNotCheckMethodResult() {
    doIt("""
      fun main() {
        Cls().<error descr="[checkReturnValue] Return value must be checked">someAction()</error>
      }
    """.trimIndent())
  }

  fun testUsageInsideFunction() {
    doIt("""
      fun main() {
        println(someAction())
      }
    """.trimIndent())
  }

  fun testVariableAssignment() {
    doIt("""
      fun main() {
        @Suppress("unused", "UnusedVariable", "UNUSED_VARIABLE")
        val ignored = someAction()
      }
    """.trimIndent())
  }

  fun testNotLastElementOfFunction() {
    doIt("""
      fun main() {
        <error descr="[checkReturnValue] Return value must be checked">someAction()</error>
        @Suppress("unused", "UnusedVariable", "UNUSED_VARIABLE")
        val ignored = someAction()
      }
    """.trimIndent())
  }

  fun testCallChain() {
    // TODO is it a valid case?
    doIt("""
      fun main() {
        someAction().toString()
      }
    """.trimIndent())
  }

  fun testLambda() {
    doIt("""
      fun makeLambda(): () -> Int = {
        someAction()
      } 

      fun main() {
        makeLambda()
      }
    """.trimIndent())
  }

  fun testNotLastElementOfLambda() {
    doIt("""
      fun makeLambda(): () -> Int = {
        <error descr="[checkReturnValue] Return value must be checked">someAction()</error>  
        someAction()
      } 

      fun main() {
        makeLambda()
      }
    """.trimIndent())
  }

  fun testVoidLambda() {
    doIt("""
      fun lambdaConsumer(body: suspend () -> Unit) { body.toString() }

      fun main() {
        lambdaConsumer {
          <error descr="[checkReturnValue] Return value must be checked">someActionSuspending()</error>
        }
      }
    """.trimIndent())
  }

  fun testVoidLambdaWithContext() {
    doIt("""
      class FooBar {}

      fun FooBar.lambdaConsumer(body: suspend FooBar.() -> Unit) { body.toString() }

      fun main() {
        FooBar().lambdaConsumer {
          <error descr="[checkReturnValue] Return value must be checked">someActionSuspending()</error>
        }
      }
    """.trimIndent())
  }

  fun testVoidLambdaWithContext2() {
    doIt("""
      class IjentApi {}

      private fun simpleTest(body: suspend IjentApi.() -> Unit): suspend IjentApi.() -> suspend () -> Unit =
        init@{
          {
            this@init.body()
          }
        }

      @Suppress("UNUSED_PARAMETER")
      fun <T> listOf(vararg obj: T): List<T> { throw RuntimeException() }

      val strategies: List<suspend IjentApi.() -> suspend () -> Unit> = listOf(
        simpleTest {
          <error descr="[checkReturnValue] Return value must be checked">someActionSuspending()</error>
        },
      )
    """.trimIndent())
  }

  fun testRunBlocking() {
    doIt("""
      fun main() {
        kotlinx.coroutines.runBlocking {
          <error descr="[checkReturnValue] Return value must be checked">someActionSuspending()</error>
        }
      }
    """.trimIndent())
  }

  private fun doIt(@Language("kotlin") source: String) {
    @Language("kotlin")
    val lib = """
      @org.jetbrains.annotations.CheckReturnValue
      fun someAction(): Int = 123

      @Suppress("RedundantSuspendModifier")
      @org.jetbrains.annotations.CheckReturnValue
      suspend fun someActionSuspending(): Int = 123

      class Cls {
        @org.jetbrains.annotations.CheckReturnValue
        fun someAction(): Int = 123
      }
    """.trimIndent()

    myFixture.configureByText("Lib.kt", lib)
    myFixture.configureByText("Example.kt", source)

    myFixture.testHighlighting("Example.kt")
  }

  override val pluginMode: KotlinPluginMode = KotlinPluginMode.of(false)  // TODO true
}
