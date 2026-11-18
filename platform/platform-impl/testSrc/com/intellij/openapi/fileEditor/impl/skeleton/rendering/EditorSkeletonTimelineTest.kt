// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

internal class EditorSkeletonTimelineTest {
  private val timeline = EditorSkeletonTimeline(fadeInMs = FADE_IN_MS, pulseEnabled = true, pulseDurationMs = PULSE_MS)

  @Test
  fun `the fade starts at the background`() {
    assertThat(timeline.stateAt(0)).isEqualTo(EditorSkeletonFrameState(fadeProgress = 0.0, opacity = 1.0))
  }

  @Test
  fun `a short open shows the blocks below one just noticeable difference`() {
    assertThat(timeline.stateAt(300).fadeProgress).isLessThan(ONE_JND_PROGRESS)
  }

  @Test
  fun `the fade rises to full contrast at the end of the fade-in`() {
    val progress = (0L..FADE_IN_MS step FRAME_MS).map { timeline.stateAt(it).fadeProgress }
    assertThat(progress).isSorted()
    assertThat(timeline.stateAt(FADE_IN_MS).fadeProgress).isEqualTo(1.0)
  }

  @Test
  fun `the fade reaches full contrast with almost zero speed`() {
    val lastStep = timeline.stateAt(FADE_IN_MS).fadeProgress - timeline.stateAt(FADE_IN_MS - FRAME_MS).fadeProgress
    assertThat(lastStep).isLessThan(0.001)
  }

  @Test
  fun `the pulse starts at the peak without a jump`() {
    assertThat(timeline.stateAt(FADE_IN_MS).opacity).isCloseTo(1.0, within(1e-9))
    assertThat(timeline.stateAt(FADE_IN_MS + FRAME_MS).opacity).isGreaterThan(0.99)
  }

  @Test
  fun `the pulse stays between its limits and repeats with its period`() {
    val opacity = (FADE_IN_MS..FADE_IN_MS + PULSE_MS step FRAME_MS).map { timeline.stateAt(it).opacity }
    assertThat(opacity).allSatisfy { assertThat(it).isBetween(0.3 - 1e-9, 1.0 + 1e-9) }
    assertThat(timeline.stateAt(FADE_IN_MS + PULSE_MS / 2).opacity).isCloseTo(0.3, within(1e-9))
    assertThat(timeline.stateAt(FADE_IN_MS + 200)).isEqualTo(timeline.stateAt(FADE_IN_MS + PULSE_MS + 200))
  }

  @Test
  fun `without the pulse the blocks stay at full contrast`() {
    val timeline = EditorSkeletonTimeline(fadeInMs = FADE_IN_MS, pulseEnabled = false, pulseDurationMs = PULSE_MS)
    assertThat(timeline.stateAt(FADE_IN_MS + PULSE_MS / 2)).isEqualTo(EditorSkeletonFrameState(fadeProgress = 1.0, opacity = 1.0))
  }

  @Test
  fun `a pulse duration of zero turns the pulse off`() {
    val timeline = EditorSkeletonTimeline(fadeInMs = FADE_IN_MS, pulseEnabled = true, pulseDurationMs = 0)
    assertThat(timeline.stateAt(FADE_IN_MS + PULSE_MS / 2)).isEqualTo(EditorSkeletonFrameState(fadeProgress = 1.0, opacity = 1.0))
  }

  @Test
  fun `a fade-in of zero gives full contrast at once`() {
    val timeline = EditorSkeletonTimeline(fadeInMs = 0, pulseEnabled = false, pulseDurationMs = PULSE_MS)
    assertThat(timeline.stateAt(0)).isEqualTo(EditorSkeletonFrameState(fadeProgress = 1.0, opacity = 1.0))
  }
}

private const val FADE_IN_MS = 1500L
private const val PULSE_MS = 1500L
private const val FRAME_MS = 8L

/**
 * The fade progress of one just noticeable difference: an Oklab lightness change of 0.02 out of the full 0.13.
 */
private const val ONE_JND_PROGRESS = 0.02 / 0.13
