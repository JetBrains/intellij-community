// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.ui.AgentWorkbenchHintBanner
import com.intellij.agent.workbench.sessions.core.ui.AgentWorkbenchHintBannerState
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JPanel

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchHintBannerTest {
  @Test
  fun syncVisibilityAcknowledgesWhenEligibleAndFeatureIsEnabled() {
    lateinit var banner: TestHintBanner

    try {
      runInEdtAndWait {
        banner = TestHintBanner()
        banner.updateEligible(true)
        banner.updateFeatureEnabled(true)

        assertThat(banner.acknowledgeCount).isEqualTo(1)
        assertThat(banner.isVisible).isFalse()
      }
    }
    finally {
      runInEdtAndWait { banner.removeNotify() }
    }
  }

  @Test
  fun visibilityChangesRefreshParentLayout() {
    lateinit var banner: TestHintBanner
    lateinit var parent: RefreshTrackingPanel

    try {
      runInEdtAndWait {
        banner = TestHintBanner()
        parent = RefreshTrackingPanel().apply {
          add(banner)
        }

        banner.updateEligible(true)

        assertThat(banner.isVisible).isTrue()
        assertThat(parent.revalidateCount).isGreaterThan(0)
        assertThat(parent.repaintCount).isGreaterThan(0)

        parent.resetRefreshCounters()
        banner.updateFeatureEnabled(true)

        assertThat(banner.isVisible).isFalse()
        assertThat(parent.revalidateCount).isGreaterThan(0)
        assertThat(parent.repaintCount).isGreaterThan(0)
      }
    }
    finally {
      runInEdtAndWait {
        banner.removeNotify()
        parent.removeAll()
      }
    }
  }

  @Test
  fun enableButtonInvokesFeatureEnableAndAcknowledgesHint() {
    lateinit var banner: TestHintBanner

    try {
      runInEdtAndWait {
        banner = TestHintBanner()

        banner.findButton("Enable").doClick()

        assertThat(banner.enableCount).isEqualTo(1)
        assertThat(banner.acknowledgeCount).isEqualTo(1)
      }
    }
    finally {
      runInEdtAndWait { banner.removeNotify() }
    }
  }
}

private class TestHintBanner : AgentWorkbenchHintBanner(
  titleText = "Title",
  bodyText = "Body",
  enableText = "Enable",
  dismissText = "Dismiss",
) {
  var enableCount: Int = 0
    private set

  var acknowledgeCount: Int = 0
    private set

  fun updateEligible(eligible: Boolean) {
    updateEligibility(eligible)
  }

  fun updateFeatureEnabled(featureEnabled: Boolean) {
    updateFeatureEnabledState(featureEnabled)
  }

  override fun enableFeature() {
    enableCount++
  }

  override fun acknowledgeHint() {
    acknowledgeCount++
  }

  override fun shouldAcknowledge(state: AgentWorkbenchHintBannerState): Boolean {
    return state.eligible && !state.acknowledged && state.featureEnabled
  }

  override fun shouldShow(state: AgentWorkbenchHintBannerState): Boolean {
    return state.eligible && !state.acknowledged && !state.featureEnabled
  }
}

private class RefreshTrackingPanel : JPanel() {
  var revalidateCount: Int = 0
    private set

  var repaintCount: Int = 0
    private set

  override fun revalidate() {
    revalidateCount++
    super.revalidate()
  }

  override fun repaint() {
    repaintCount++
    super.repaint()
  }

  fun resetRefreshCounters() {
    revalidateCount = 0
    repaintCount = 0
  }
}

private fun JPanel.findButton(text: String): JButton {
  components.forEach { component ->
    when (component) {
      is JButton -> if (component.text == text) return component
      is JPanel -> {
        val nested = component.findButtonOrNull(text)
        if (nested != null) return nested
      }
    }
  }
  error("Button '$text' not found")
}

private fun JPanel.findButtonOrNull(text: String): JButton? {
  components.forEach { component ->
    when (component) {
      is JButton -> if (component.text == text) return component
      is JPanel -> {
        val nested = component.findButtonOrNull(text)
        if (nested != null) return nested
      }
    }
  }
  return null
}
