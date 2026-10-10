// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.refactoring.modplan.impl.ModPlanSubjectConfirmation
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ModPlanSubjectConfirmationTest : BasePlatformTestCase() {
  private val switch = ModPlanOption.bool("switch", true) { "Switch" }
  private val keys = ModPlanOption.text("keys", "") { "Keys" }

  /** @param answer what the dialog answers: true for yes, false for no, null for the cancel */
  private fun question(key: String?, answer: Boolean? = true): ModPlanSubjectConfirmation =
    ModPlanSubjectConfirmation(switch, keys, key) { answer }

  fun `test the answer of one subject does not answer another subject`() {
    val answers = question("a", answer = false).ask(project, ModPlanOptionValues.EMPTY)!!

    assertTrue(question("a").isAnswered(answers))
    assertFalse(question("a").isConfirmed(answers))
    assertFalse(question("b").isAnswered(answers))
  }

  fun `test the answers of two subjects add up`() {
    val firstAnswers = question("a", answer = true).ask(project, ModPlanOptionValues.EMPTY)!!
    val answers = question("b", answer = false).ask(project, firstAnswers)!!

    assertTrue(question("a").isConfirmed(answers))
    assertFalse(question("b").isConfirmed(answers))
  }

  fun `test the switch answers every subject`() {
    val keepValues = ModPlanOptionValues.EMPTY.with(switch, false)

    assertTrue(question("a").isAnswered(keepValues))
    assertFalse(question("a").isConfirmed(keepValues))
    assertFalse(question("b").isConfirmed(keepValues.with(keys, "b")))
  }

  fun `test the chosen keys of a request answer their subjects, and a list of other keys only is no`() {
    val requestValues = ModPlanOptionValues.EMPTY.with(keys, "a")

    assertTrue(question("a").isAnswered(requestValues))
    assertTrue(question("a").isConfirmed(requestValues))
    assertFalse(question("b").isAnswered(requestValues))
    assertFalse(question("b").isConfirmed(requestValues))
  }

  fun `test with no keys the switch decides by default`() {
    assertFalse(question("a").isAnswered(ModPlanOptionValues.EMPTY))
    assertTrue(question("a").isConfirmed(ModPlanOptionValues.EMPTY))
  }

  fun `test a subject with no key answers with the switch`() {
    val answers = question(null, answer = false).ask(project, ModPlanOptionValues.EMPTY)!!

    assertEquals(false, answers.get(switch))
    assertTrue(question("b").isAnswered(answers))
  }

  fun `test the cancel of the dialog gives no answers`() {
    assertNull(question("a", answer = null).ask(project, ModPlanOptionValues.EMPTY))
  }
}
