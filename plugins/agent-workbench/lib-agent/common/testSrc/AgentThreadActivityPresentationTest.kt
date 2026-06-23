// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.common

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Color
import java.util.concurrent.TimeUnit
import javax.swing.UIManager

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentThreadActivityPresentationTest {
  @Test
  fun usesThreadPresentationsAndMessageKeys() {
    val expected = linkedMapOf(
      AgentThreadActivity.READY to AgentThreadActivityPresentation(
        namedColorKey = null,
        lightFallbackRgb = null,
        darkFallbackRgb = null,
        statusMessageKey = "toolwindow.thread.status.ready",
        showBadge = false,
      ),
      AgentThreadActivity.PROCESSING to AgentThreadActivityPresentation(
        namedColorKey = "IconBadge.successBackground",
        lightFallbackRgb = Color(0x55A76A).rgb,
        darkFallbackRgb = Color(0x5FAD65).rgb,
        statusMessageKey = "toolwindow.thread.status.in.progress",
      ),
      AgentThreadActivity.REVIEWING to AgentThreadActivityPresentation(
        namedColorKey = "IconBadge.warningBackground",
        lightFallbackRgb = Color(0xFFAF0F).rgb,
        darkFallbackRgb = Color(0xF2C55C).rgb,
        statusMessageKey = "toolwindow.thread.status.needs.review",
      ),
      AgentThreadActivity.UNREAD to AgentThreadActivityPresentation(
        namedColorKey = "IconBadge.infoBackground",
        lightFallbackRgb = Color(0x588CF3).rgb,
        darkFallbackRgb = Color(0x548AF7).rgb,
        statusMessageKey = "toolwindow.thread.status.done",
      ),
      AgentThreadActivity.NEEDS_INPUT to AgentThreadActivityPresentation(
        namedColorKey = "IconBadge.warningBackground",
        lightFallbackRgb = Color(0xFFAF0F).rgb,
        darkFallbackRgb = Color(0xF2C55C).rgb,
        statusMessageKey = "toolwindow.thread.status.needs.input",
      ),
    )

    expected.forEach { (activity, presentation) ->
      assertThat(activity.presentation()).isEqualTo(presentation)
      val statusColor = activity.statusColor()
      if (presentation.namedColorKey == null) {
        assertThat(statusColor).isNull()
      }
      else {
        assertThat(statusColor?.rgb).isIn(
          Color(requireNotNull(presentation.lightFallbackRgb)).rgb,
          Color(requireNotNull(presentation.darkFallbackRgb)).rgb,
        )
      }
      assertThat(activity.statusBadgeColor()?.rgb).isEqualTo(if (presentation.showBadge) statusColor?.rgb else null)
      assertThat(activity.statusMessageKey()).isEqualTo(presentation.statusMessageKey)
    }
  }

  @Test
  fun prefersUiManagerOverrideForNamedColor() {
    val overrideColor = Color(0x112233)

    withTemporaryNeedsInputUiColor(overrideColor) {
      assertThat(AgentThreadActivity.NEEDS_INPUT.statusColor()?.rgb).isEqualTo(overrideColor.rgb)
      assertThat(AgentThreadActivity.NEEDS_INPUT.statusBadgeColor()?.rgb).isEqualTo(overrideColor.rgb)
    }
  }

  private fun withTemporaryNeedsInputUiColor(color: Color, action: () -> Unit) {
    val key = "IconBadge.warningBackground"
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
