// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.modcommand.ModEditOptions
import com.intellij.model.SymbolPointer
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ModPlanCommandInterpreterTest : BasePlatformTestCase() {
  private val flag = ModPlanOption.bool("flag", false) { "Flag" }
  private var firstStepRuns = 0
  private val collectedFlags = ArrayList<Boolean>()

  override fun setUp() {
    super.setUp()
    TestModeFlags.set(ModPlanCommandInterpreter.ASKS_IN_TESTS, true, testRootDisposable)
  }

  private fun requestOf(target: PsiElement): ModPlanRequest = inBackground { ModPlanRequest(project, listOf(SymbolPointer.of(target)), ModPlanOptionValues.EMPTY) }

  /** The chain asks [flag], and its last step records the answer. */
  private fun command(
    request: ModPlanRequest,
    storage: ModPlanQuestion.Storage? = null,
    loadsRememberedValues: Boolean = true,
  ): ModCommand = inBackground {
    ModPlanCommandInterpreter.interpret("Title", request, loadsRememberedValues) { chain ->
      firstStepRuns++
      chain.ask(ModPlanQuestion.Form.of(listOf(flag), storage)) { answered ->
        collectedFlags.add(answered.request.optionValues().get(flag))
        answered.passValue(ModCommand.nop())
      }
    }
  }

  fun `test the answer of a question goes on with the chain`() {
    val command = command(requestOf(myFixture.addFileToProject("a.txt", "a"))) as ModEditOptions<*>

    inBackground { command.applyOptions(mapOf("flag" to true)) }

    assertEquals(listOf(true), collectedFlags)
    assertEquals(1, firstStepRuns)
  }

  fun `test a change of PSI at a question runs the chain again with the answer`() {
    val file = myFixture.addFileToProject("a.txt", "a")
    val command = command(requestOf(file)) as ModEditOptions<*>
    WriteCommandAction.runWriteCommandAction(project) { file.viewProvider.document!!.insertString(0, "b") }
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    inBackground { command.applyOptions(mapOf("flag" to true)) }

    assertEquals(listOf(true), collectedFlags)
    assertEquals(2, firstStepRuns)
  }

  fun `test batch execution takes the value that the storage remembers`() {
    val file = myFixture.addFileToProject("a.txt", "a")
    val storage = object : ModPlanQuestion.Storage {
      override fun load(optionValues: ModPlanOptionValues): ModPlanOptionValues = optionValues.with(flag, true)

      override fun store(answers: ModPlanOptionValues) = fail("A command must not store the answers")
    }
    val command = command(requestOf(file), storage)

    ModCommandExecutor.getInstance().executeInBatch(ActionContext.from(null, file), command)

    assertEquals(listOf(true), collectedFlags)
  }

  fun `test a run without the remembered values takes the values of the request`() {
    val file = myFixture.addFileToProject("a.txt", "a")
    val storage = object : ModPlanQuestion.Storage {
      override fun load(optionValues: ModPlanOptionValues): ModPlanOptionValues = optionValues.with(flag, true)

      override fun store(answers: ModPlanOptionValues) = fail("A command must not store the answers")
    }
    val command = command(requestOf(file), storage, loadsRememberedValues = false)

    ModCommandExecutor.getInstance().executeInBatch(ActionContext.from(null, file), command)

    assertEquals(listOf(false), collectedFlags)
  }

  fun `test an answered question is not asked`() {
    val file = myFixture.addFileToProject("a.txt", "a")
    val request = inBackground { ModPlanRequest(project, listOf(SymbolPointer.of(file)), ModPlanOptionValues.EMPTY.with(flag, true)) }

    assertEquals(ModCommand.nop(), command(request))
    assertEquals(listOf(true), collectedFlags)
  }
}
