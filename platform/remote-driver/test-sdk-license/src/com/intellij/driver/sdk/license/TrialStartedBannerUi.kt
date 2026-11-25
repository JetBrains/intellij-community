@file:Suppress("PublicTestExtensionFunction")

package com.intellij.driver.sdk.license

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.ui

/**
 * The 'trial started' banner on IDE Home (the non-modal welcome screen).
 * IDE Home of a free mode IDE shows it to a new user with an active trial.
 */
fun Finder.trialStartedBanner() = x(TrialStartedBannerUi::class.java) {
  componentWithChild(byType("com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenBannerComponent"),
                     byVisibleText("Learn more"))
}

fun Driver.trialStartedBanner(action: TrialStartedBannerUi.() -> Unit = {}) = ui.trialStartedBanner().apply(action)

class TrialStartedBannerUi(data: ComponentData) : UiComponent(data) {
  val learnMoreLink: UiComponent = x { byType("com.intellij.ui.components.labels.LinkLabel") and byVisibleText("Learn more") }
  val activateSubscriptionLink: UiComponent =
    x { byType("com.intellij.ui.components.labels.LinkLabel") and byVisibleText("Activate subscription…") }
}
