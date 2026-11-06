// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors

import com.intellij.testFramework.assertErrorLogged
import com.intellij.testFramework.assertNoErrorLogged
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal class ColorKeyTest {
  /**
   * A key that a color scheme reads by name, with [ColorKey.find], and that a class creates with its default color at the
   * same time. `ScrollBarPainter.BACKGROUND` lost its default color this way while the IDE loaded its schemes, and every
   * scroll pane of that process then failed its assertion.
   */
  @Test
  fun `a key created with a default color keeps it while a scheme finds the key by name`() {
    repeat(ROUNDS) { round ->
      val name = "ColorKeyTest.$round"
      // The scheme read the key first, so the registry holds it with no default color.
      ColorKey.find(name)
      val stop = AtomicBoolean()
      val reader = thread(name = "scheme reader") {
        while (!stop.get()) ColorKey.find(name)
      }
      try {
        assertEquals(DEFAULT, ColorKey.createColorKey(name, DEFAULT).defaultColor, "the default color of '$name'")
      }
      finally {
        stop.set(true)
        reader.join()
      }
    }
  }

  @Test
  fun `a key created with a fallback keeps it while a scheme finds the key by name`() {
    val fallback = ColorKey.createColorKey("ColorKeyTest.fallback", DEFAULT)
    repeat(ROUNDS) { round ->
      val name = "ColorKeyTest.withFallback.$round"
      ColorKey.find(name)
      val stop = AtomicBoolean()
      val reader = thread(name = "scheme reader") {
        while (!stop.get()) ColorKey.find(name)
      }
      try {
        assertEquals(fallback, ColorKey.createColorKeyWithFallback(name, fallback).fallbackColorKey, "the fallback of '$name'")
      }
      finally {
        stop.set(true)
        reader.join()
      }
    }
  }

  @Test
  fun `a key that falls back to itself logs a cycle`() {
    val self = "ColorKeyTest.self"
    val key = ColorKey.find(self)
    val error = assertErrorLogged<Throwable> {
      ColorKey.createColorKeyWithFallback(self, key)
    }
    assertEquals("Cycle detected: $self->$self", error.message)
  }

  @Test
  fun `two keys that fall back to each other log a cycle`() {
    val a = "ColorKeyTest.a"
    val b = "ColorKeyTest.b"
    val keyB = ColorKey.createColorKey(b)
    val keyA = ColorKey.createColorKeyWithFallback(a, keyB)
    val error = assertErrorLogged<Throwable> {
      ColorKey.createColorKeyWithFallback(b, keyA)
    }
    assertEquals("Cycle detected: $b->$a->$b", error.message)
  }

  @Test
  fun `a chain of fallbacks logs no error`() {
    val base = ColorKey.createColorKey("ColorKeyTest.base")
    val middle = ColorKey.createColorKeyWithFallback("ColorKeyTest.middle", base)
    assertNoErrorLogged {
      ColorKey.createColorKeyWithFallback("ColorKeyTest.top", middle)
    }
  }

  private companion object {
    const val ROUNDS = 2_000
    val DEFAULT: Color = Color(0xF5, 0xF5, 0xF5)
  }
}
