// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Frame
import java.awt.Rectangle

class EarlyProjectFrameStateTest {
  @Test
  fun `the state survives a round trip`() {
    val state = EarlyFrameState(Rectangle(10, 22, 1398, 973), Frame.MAXIMIZED_BOTH, undecorated = true, projectOpen = true, background = 0x2b2d30)
    assertThat(parseEarlyFrameState(formatEarlyFrameState(state))).isEqualTo(state)
  }

  @Test
  fun `a missing or broken bounds line yields no state`() {
    assertThat(parseEarlyFrameState("")).isNull()
    assertThat(parseEarlyFrameState("bounds=1,2,3\n")).isNull()
    assertThat(parseEarlyFrameState("bounds=1,2,0,4\n")).isNull()
    assertThat(parseEarlyFrameState("bounds=a,b,c,d\n")).isNull()
  }

  @Test
  fun `the theme background follows the theme id`() {
    assertThat(themeBackground(null)).isEqualTo(0x191a1c)
    assertThat(themeBackground("Islands Light")).isEqualTo(0xffffff)
    assertThat(themeBackground("ExperimentalDark")).isEqualTo(0x1e1f22)
    assertThat(themeBackground("Some Vendor Light")).isEqualTo(0xffffff)
    assertThat(themeBackground("Some Vendor Theme")).isEqualTo(0x1e1f22)
  }

  @Test
  fun `the extended state and the flags default when absent`() {
    val state = parseEarlyFrameState("bounds=0,0,800,600\n")
    assertThat(state).isEqualTo(EarlyFrameState(Rectangle(0, 0, 800, 600), Frame.NORMAL, undecorated = false, projectOpen = false, background = null))
  }
}
