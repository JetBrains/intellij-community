// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color
import javax.swing.UIManager

class AgentThreadActivityPresentationTest {
  @Test
  fun usesNamedThreadColorsAndMessageKeys() {
    val expected = linkedMapOf(
      AgentThreadActivity.READY to AgentThreadActivityPresentation(
        namedColorKey = "AgentWorkbench.ThreadStatus.ready",
        lightFallbackRgb = 0x3FE47E,
        darkFallbackRgb = 0x57965C,
        statusMessageKey = "toolwindow.thread.status.ready",
      ),
      AgentThreadActivity.PROCESSING to AgentThreadActivityPresentation(
        namedColorKey = "AgentWorkbench.ThreadStatus.processing",
        lightFallbackRgb = 0xFF9F43,
        darkFallbackRgb = 0xE08855,
        statusMessageKey = "toolwindow.thread.status.in.progress",
      ),
      AgentThreadActivity.REVIEWING to AgentThreadActivityPresentation(
        namedColorKey = "AgentWorkbench.ThreadStatus.reviewing",
        lightFallbackRgb = 0x2FD1C4,
        darkFallbackRgb = 0x20B2AA,
        statusMessageKey = "toolwindow.thread.status.needs.review",
      ),
      AgentThreadActivity.UNREAD to AgentThreadActivityPresentation(
        namedColorKey = "AgentWorkbench.ThreadStatus.unread",
        lightFallbackRgb = 0x4DA3FF,
        darkFallbackRgb = 0x548AF7,
        statusMessageKey = "toolwindow.thread.status.needs.input",
      ),
    )

    expected.forEach { (activity, presentation) ->
      assertThat(activity.presentation()).isEqualTo(presentation)
      assertThat(activity.statusColor().rgb).isIn(
        Color(presentation.lightFallbackRgb).rgb,
        Color(presentation.darkFallbackRgb).rgb,
      )
      assertThat(activity.statusMessageKey()).isEqualTo(presentation.statusMessageKey)
    }
  }

  @Test
  fun prefersUiManagerOverrideForNamedColor() {
    val overrideColor = Color(0x112233)

    withTemporaryReadyUiColor(overrideColor) {
      assertThat(AgentThreadActivity.READY.statusColor().rgb).isEqualTo(overrideColor.rgb)
    }
  }

  private fun withTemporaryReadyUiColor(color: Color, action: () -> Unit) {
    val key = "AgentWorkbench.ThreadStatus.ready"
    val defaults = UIManager.getDefaults()
    val previous = defaults[key]
    UIManager.put(key, color)
    try {
      action()
    }
    finally {
      if (previous == null) {
        defaults.remove(key)
      }
      else {
        UIManager.put(key, previous)
      }
    }
  }
}
