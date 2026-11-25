@file:Suppress("PublicTestExtensionFunction")

package com.intellij.driver.sdk.license

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.ui

fun Finder.trialWidget() = x(TrialWidgetUi::class.java) {
  byAttribute("name", "TrialStateButton") or byClass("WidgetLabel")
}

fun Finder.trialWidget(action: TrialWidgetUi.() -> Unit) = trialWidget().action()

fun Driver.trialWidget(action: TrialWidgetUi.() -> Unit = {}) = ui.trialWidget().apply(action)

class TrialWidgetUi(data: ComponentData) : UiComponent(data) {
  val state: TrialWidgetState?
    // The longest match wins: 'Trial: Unknown' also starts with the 'Trial' text
    get() = TrialWidgetState.entries.filter { text.startsWith(it.text) }.maxByOrNull { it.text.length }

  val text: String
    get() = if (present()) getAllTexts().firstOrNull()?.text ?: "" else ""
}

enum class TrialWidgetState(val text: String) {
  TRIAL("Trial"),
  UNLOCK_ULTIMATE("Unlock Ultimate"),
  START_FREE_TRIAL("Start Free Trial"),
  TRIAL_EXPIRED("Trial expired"),
  REQUESTING("Requesting trial…"),
  GRACE("Grace: "),
  UNKNOWN("Trial: Unknown");
}
