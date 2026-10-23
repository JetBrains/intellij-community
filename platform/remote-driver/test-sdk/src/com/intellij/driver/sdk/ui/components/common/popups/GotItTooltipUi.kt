package com.intellij.driver.sdk.ui.components.common.popups

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Clicks the 'Got It' button of a 'Got It' tooltip, because the tooltip can cover the component that a test must use.
 * Does nothing when no tooltip shows within [appearTimeout].
 */
fun Finder.dismissGotItTooltipIfShown(appearTimeout: Duration = 5.seconds) {
  step("Dismiss the 'Got It' tooltip if it is shown") {
    val gotItButton = button("Got It")
    val shown = runCatching { waitFor("'Got It' tooltip", appearTimeout) { gotItButton.present() } }.isSuccess
    if (shown) {
      gotItButton.click()
      waitFor("'Got It' tooltip to close", 10.seconds) { gotItButton.notPresent() }
    }
  }
}
