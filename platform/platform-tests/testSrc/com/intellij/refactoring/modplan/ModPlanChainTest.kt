// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.model.SymbolPointer
import com.intellij.testFramework.ModPlanTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ModPlanChainTest : BasePlatformTestCase() {
  private val flag = ModPlanOption.bool("flag", false) { "Flag" }

  private fun requestOf(): ModPlanRequest {
    val file = myFixture.addFileToProject("a.txt", "a")
    return inBackground { ModPlanRequest(project, listOf(SymbolPointer.of(file)), ModPlanOptionValues.EMPTY) }
  }

  /** @return the exception of the run of [chain] in a background read action; null if the run ends */
  private fun failureOf(chain: (ModPlanChainBuilder) -> ModPlanChain<Int>): Throwable? {
    val request: ModPlanRequest = requestOf()
    return inBackground { runCatching { ModPlanCommandInterpreter.interpretHeadless(request, chain) }.exceptionOrNull() }
  }

  fun `test a builder builds one chain`() {
    val failure = failureOf { chainBuilder ->
      chainBuilder.passValue(1)
      chainBuilder.passValue(2)
    }

    assertInstanceOf(failure, IllegalStateException::class.java)
  }

  fun `test a builder that asked builds no other chain`() {
    val failure = failureOf { chainBuilder ->
      chainBuilder.ask(ModPlanQuestion.Form.of(listOf(flag), null)) { it.passValue(1) }
      chainBuilder.passValue(2)
    }

    assertInstanceOf(failure, IllegalStateException::class.java)
  }

  fun `test the function after an answer gets a builder of its own`() {
    val request = requestOf()

    val result = inBackground {
      ModPlanCommandInterpreter.interpretHeadless(request) { chainBuilder: ModPlanChainBuilder ->
        chainBuilder.ask(ModPlanQuestion.Form.of(listOf(flag), null)) { answered -> answered.passValue(1) }
          .andThen { afterValue, value -> afterValue.passValue(value + 1) }
      }
    }

    assertEquals(2, result)
  }

  fun `test a final step runs only when the caller runs it, after the questions that come later`() {
    val request = requestOf()
    val logEntries = ArrayList<String>()

    val result = inBackground {
      ModPlanTestUtil.runAnswering(request, { chainBuilder: ModPlanChainBuilder ->
        chainBuilder.finish { logEntries.add("final step"); 1 }
          .andThen { afterStep, finalStep ->
            afterStep.ask(ModPlanQuestion.Form.of(listOf(flag), null)) { answered -> answered.finish { finalStep.run(it) + 1 } }
          }
      }) { _, currentValues ->
        logEntries.add("question")
        currentValues.with(flag, true)
      }.run(request)
    }

    assertEquals(listOf("question", "final step"), logEntries)
    assertEquals(2, result)
  }
}
