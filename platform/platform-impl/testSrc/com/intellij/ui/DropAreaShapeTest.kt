// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Rectangle

/**
 * Tests [createDropAreaShape]: a corner of the drop placeholder is rounded only at a corner of the corner area.
 */
internal class DropAreaShapeTest {
  @Test
  fun `the placeholder over the top of the right window rounds only the top right corner`() {
    val rightWindowTop = Rectangle(WIDTH / 2, 0, WIDTH / 2, HEIGHT / 2)
    val shape = createDropAreaShape(rightWindowTop, CORNER_AREA, ARC)
    assertThat(shape.bounds).isEqualTo(rightWindowTop)
    assertThat(shape.contains(WIDTH - 0.5, 0.5)).isFalse()
    assertThat(shape.contains(WIDTH / 2 + 0.5, 0.5)).isTrue()
    assertThat(shape.contains(WIDTH / 2 + 0.5, HEIGHT / 2 - 0.5)).isTrue()
    assertThat(shape.contains(WIDTH - 0.5, HEIGHT / 2 - 0.5)).isTrue()
  }

  @Test
  fun `a corner inside the insets of the corner area is rounded`() {
    val shape = createDropAreaShape(Rectangle(2, 2, WIDTH - 4, HEIGHT - 4), CORNER_AREA, ARC)
    assertThat(shape.contains(2.5, 2.5)).isFalse()
    assertThat(shape.contains(WIDTH - 2.5, 2.5)).isFalse()
    assertThat(shape.contains(2.5, HEIGHT - 2.5)).isFalse()
    assertThat(shape.contains(WIDTH - 2.5, HEIGHT - 2.5)).isFalse()
  }
}

private const val WIDTH = 1000
private const val HEIGHT = 200
private const val ARC = 20
private val CORNER_AREA = Rectangle(0, 0, WIDTH, HEIGHT)
