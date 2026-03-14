// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.testFramework.UsefulTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class BadgeTest : UsefulTestCase() {

  @Test
  fun `default colorType is BLUE_SECONDARY`() {
    val badge = Badge("Test")
    assertEquals(BadgeColorType.BLUE_SECONDARY, badge.colorType)
  }

  @Test
  fun `default disabled is false`() {
    val badge = Badge("Test")
    assertFalse(badge.disabled)
  }

  @Test
  fun `custom colorType is preserved`() {
    for (type in BadgeColorType.entries) {
      val badge = Badge("X", type)
      assertEquals(type, badge.colorType)
    }
  }

  @Test
  fun `disabled flag is preserved`() {
    val badge = Badge("Test", disabled = true)
    assertTrue(badge.disabled)
  }

  @Test
  fun `text is preserved`() {
    val badge = Badge("Hello")
    assertEquals("Hello", badge.text)
  }

  @Test
  fun `newBadge has BLUE colorType`() {
    val badge = Badge.newBadge()
    assertEquals(BadgeColorType.BLUE, badge.colorType)
  }

  @Test
  fun `betaBadge has PURPLE_SECONDARY colorType`() {
    val badge = Badge.betaBadge()
    assertEquals(BadgeColorType.PURPLE_SECONDARY, badge.colorType)
  }

  @Test
  fun `freeBadge has GREEN colorType`() {
    val badge = Badge.freeBadge()
    assertEquals(BadgeColorType.GREEN, badge.colorType)
  }

  @Test
  fun `trialBadge has GREEN_SECONDARY colorType`() {
    val badge = Badge.trialBadge()
    assertEquals(BadgeColorType.GREEN_SECONDARY, badge.colorType)
  }

  @Test
  fun `factory badges are not disabled`() {
    assertFalse(Badge.newBadge().disabled)
    assertFalse(Badge.betaBadge().disabled)
    assertFalse(Badge.freeBadge().disabled)
    assertFalse(Badge.trialBadge().disabled)
  }

  @Test
  fun `iconHeight is positive`() {
    val badge = Badge("Test")
    assertTrue("iconHeight should be > 0", badge.iconHeight > 0)
  }

  @Test
  fun `iconWidth is positive`() {
    val badge = Badge("Test")
    assertTrue("iconWidth should be > 0", badge.iconWidth > 0)
  }

  @Test
  fun `iconWidth increases with longer text`() {
    val short = Badge("A")
    val long = Badge("A longer text badge")
    assertTrue("Longer text should produce wider icon", long.iconWidth > short.iconWidth)
  }

  @Test
  fun `all BadgeColorType entries are covered`() {
    // Ensure every color type can be used to construct a badge without error
    for (type in BadgeColorType.entries) {
      val badge = Badge("Test", type)
      assertEquals(type, badge.colorType)
      assertTrue("iconWidth should be > 0 for $type", badge.iconWidth > 0)
      assertTrue("iconHeight should be > 0 for $type", badge.iconHeight > 0)
    }
  }
}
