// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.sessions.waitForCondition
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import javax.swing.JPanel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsSwingQuotaHintTest {
  @Test
  fun hintVisibilityRequiresEligibleUnacknowledgedAndDisabledWidget() {
    assertThat(shouldShowClaudeQuotaHint(eligible = true, acknowledged = false, widgetEnabled = false)).isTrue()
    assertThat(shouldShowClaudeQuotaHint(eligible = false, acknowledged = false, widgetEnabled = false)).isFalse()
    assertThat(shouldShowClaudeQuotaHint(eligible = true, acknowledged = true, widgetEnabled = false)).isFalse()
    assertThat(shouldShowClaudeQuotaHint(eligible = true, acknowledged = false, widgetEnabled = true)).isFalse()
  }

  @Test
  fun acknowledgementRequiresEligibleUnacknowledgedAndEnabledWidget() {
    assertThat(shouldAcknowledgeClaudeQuotaHint(eligible = true, acknowledged = false, widgetEnabled = true)).isTrue()
    assertThat(shouldAcknowledgeClaudeQuotaHint(eligible = false, acknowledged = false, widgetEnabled = true)).isFalse()
    assertThat(shouldAcknowledgeClaudeQuotaHint(eligible = true, acknowledged = true, widgetEnabled = true)).isFalse()
    assertThat(shouldAcknowledgeClaudeQuotaHint(eligible = true, acknowledged = false, widgetEnabled = false)).isFalse()
  }

  @Test
  fun hintBannerRequestsParentRefreshWhenAcknowledged() = runBlocking {
    val hintStateService = ClaudeQuotaHintStateService().apply { markEligible() }
    assertBannerRequestsParentRefreshAfterHide(
      bannerFactory = { ClaudeQuotaHintBanner(hintStateService) },
      hideBanner = { hintStateService.acknowledge() },
    )
  }
}

private suspend fun assertBannerRequestsParentRefreshAfterHide(
  bannerFactory: () -> JPanel,
  hideBanner: () -> Unit,
) {
  lateinit var banner: JPanel
  lateinit var parent: RefreshTrackingPanel

  try {
    runInEdtAndWait {
      banner = bannerFactory()
      parent = RefreshTrackingPanel().apply {
        add(banner)
      }
    }

    waitForCondition { isVisibleForLayoutTest(banner) }
    runInEdtAndWait { parent.resetRefreshCounters() }

    runInEdtAndWait { hideBanner() }

    waitForCondition { !isVisibleForLayoutTest(banner) }
    waitForCondition {
      refreshCountsForTest(parent).let { it.revalidateCount > 0 && it.repaintCount > 0 }
    }
  }
  finally {
    runInEdtAndWait {
      banner.removeNotify()
      parent.removeAll()
    }
  }
}

private fun isVisibleForLayoutTest(component: JPanel): Boolean {
  var visible = false
  runInEdtAndWait { visible = component.isVisible }
  return visible
}

private fun refreshCountsForTest(panel: RefreshTrackingPanel): RefreshCounts {
  var counts = RefreshCounts(revalidateCount = 0, repaintCount = 0)
  runInEdtAndWait {
    counts = RefreshCounts(revalidateCount = panel.revalidateCount, repaintCount = panel.repaintCount)
  }
  return counts
}

private data class RefreshCounts(
  val revalidateCount: Int,
  val repaintCount: Int,
)

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
