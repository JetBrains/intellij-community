package com.intellij.driver.sdk.ui.components.plugins

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

fun Finder.pluginUpdateBalloon(
  pluginName: String,
  action: PluginUpdateBalloonUiComponent.() -> Unit = {},
) = x(PluginUpdateBalloonUiComponent::class.java) {
  componentWithChild(byType("""com.intellij.ui.BalloonImpl${"$"}MyComponent"""),
                     contains(byAccessibleName("$pluginName plugin update available")))
}.apply(action)

class PluginUpdateBalloonUiComponent(data: ComponentData) : UiComponent(data) {
  val detailsButton = x { byVisibleText("Details…") }
  val updateButton = x { byVisibleText("Update") }
  val ignoreButton = x { byVisibleText("Ignore this update") }
}
