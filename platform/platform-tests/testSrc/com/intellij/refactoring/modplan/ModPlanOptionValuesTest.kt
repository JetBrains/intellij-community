// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ModPlanOptionValuesTest {
  private val enabledByDefault = ModPlanOption.bool("enabledByDefault", true) { "Enabled by default" }
  private val disabledByDefault = ModPlanOption.bool("disabledByDefault", false) { "Disabled by default" }
  private val mode = ModPlanOption.choice("mode", Mode::class.java, Mode.FIRST, { "Mode" }) { it.name.lowercase() }

  @Test
  fun `explicit value wins`() {
    val optionValues = ModPlanOptionValues.EMPTY.with(enabledByDefault, false).with(disabledByDefault, true)

    assertFalse(optionValues.get(enabledByDefault))
    assertTrue(optionValues.get(disabledByDefault))
  }

  @Test
  fun `absent option yields default`() {
    assertTrue(ModPlanOptionValues.EMPTY.get(enabledByDefault))
    assertFalse(ModPlanOptionValues.EMPTY.get(disabledByDefault))
  }

  @Test
  fun `equality is by id`() {
    val sameIdOption = ModPlanOption.bool("enabledByDefault", false) { "Another description" }

    assertEquals(enabledByDefault, sameIdOption)
    assertEquals(enabledByDefault.hashCode(), sameIdOption.hashCode())
    assertFalse(ModPlanOptionValues.EMPTY.with(sameIdOption, false).get(enabledByDefault))
  }

  @Test
  fun `invalid id is rejected`() {
    for (id in listOf("", "Upper", "1digit", "with-dash", "with space", "under_score")) {
      assertThrows<IllegalArgumentException>(id) { ModPlanOption.bool(id, true) { "" } }
    }
  }

  @Test
  fun `options with the same values are equal`() {
    assertEquals(ModPlanOptionValues.EMPTY.with(enabledByDefault, false), ModPlanOptionValues.EMPTY.with(enabledByDefault, false))
    assertEquals(ModPlanOptionValues.EMPTY.with(enabledByDefault, false).hashCode(), ModPlanOptionValues.EMPTY.with(enabledByDefault, false).hashCode())
    assertNotEquals(ModPlanOptionValues.EMPTY, ModPlanOptionValues.EMPTY.with(enabledByDefault, false))
  }

  private enum class Mode { FIRST, SECOND }

  @Test
  fun `choice option takes only its enum`() {
    assertEquals(Mode.FIRST, ModPlanOptionValues.EMPTY.get(mode))
    assertEquals(Mode.SECOND, ModPlanOptionValues.EMPTY.with(mode, Mode.SECOND).get(mode))
    assertEquals("second", mode.valueText!!.apply(Mode.SECOND))
  }

  @Test
  fun `answers keep the chosen keys and mark the other asked keys as not chosen`() {
    val keys = ModPlanOption.text("keys", "") { "Keys" }

    val answers = ModPlanOptionValues.EMPTY.withAnswers(keys, listOf("a", "b"), listOf("a"))

    assertEquals("a\n-b", answers.get(keys))
    assertEquals(setOf("a"), answers.chosenKeys(keys))
    assertEquals(setOf("a", "b"), answers.answeredKeys(keys))
  }

  @Test
  fun `the answers of two questions add up and a chosen key stays chosen`() {
    val keys = ModPlanOption.text("keys", "") { "Keys" }
    val firstAnswers = ModPlanOptionValues.EMPTY.withAnswers(keys, listOf("a", "b"), listOf("a"))

    val bothAnswers = firstAnswers.withAnswers(keys, listOf("a", "b", "c"), listOf("b"))

    assertEquals(setOf("a", "b"), bothAnswers.chosenKeys(keys))
    assertEquals(setOf("a", "b", "c"), bothAnswers.answeredKeys(keys))
  }

  @Test
  fun `the keys of a caller are chosen`() {
    val keys = ModPlanOption.text("keys", "") { "Keys" }

    val requestValues = ModPlanOptionValues.EMPTY.with(keys, "a\n b \n\n")

    assertEquals(setOf("a", "b"), requestValues.chosenKeys(keys))
    assertEquals(setOf("a", "b"), requestValues.answeredKeys(keys))
  }
}
