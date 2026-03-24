// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.testFramework.UsefulTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class BadgeTest : UsefulTestCase() {

  @Test
  fun `iconSize is positive`() {
    val badge = Badge("Test")
    assertTrue(badge.iconWidth > 0)
    assertTrue(badge.iconHeight > 0)
  }

  @Test
  fun `iconWidth increases with longer text`() {
    val short = Badge("A short")
    val long = Badge("A longer text badge")
    assertTrue(long.iconWidth > short.iconWidth)
  }

  @Test
  fun `iconHeight is the same`() {
    val short = Badge("A short")
    val long = Badge("A longer text badge")
    assertEquals(long.iconHeight, short.iconHeight)
  }
}
