package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

class LetsPlotComponent(data: ComponentData) : UiComponent(data) {
  private val plot by lazy { driver.cast(component, LetsPlotComponentRef::class) }

  val htmlSource: String
    get() = plot.getPlotHtml()

  fun toggleToolbar(): Unit = plot.toggleToolbar()

  val toolbar: PlotPanelToolbar
    get() = x(xpath = "//div[@class='PlotPanelToolbar']", PlotPanelToolbar::class.java)

  class PlotPanelToolbar(data: ComponentData) : UiComponent(data) {
    val panButton: JButtonUiComponent
      get() = x(xpath = "//div[@tooltiptext='Pan']", JButtonUiComponent::class.java)
    val rubberBandZoomButton: JButtonUiComponent
      get() = x(xpath = "//div[@tooltiptext='Rubber Band Zoom']", JButtonUiComponent::class.java)
    val centerPointZoomButton: JButtonUiComponent
      get() = x(xpath = "//div[@tooltiptext='Centerpoint Zoom']", JButtonUiComponent::class.java)
    val resetButton: JButtonUiComponent
      get() = x(xpath = "//div[@tooltiptext='Reset']", JButtonUiComponent::class.java)
  }
}

@Remote("com.intellij.kotlin.jupyter.plots.LetsPlotComponent", plugin = "org.jetbrains.plugins.kotlin.jupyter/intellij.kotlin.jupyter.plots")
interface LetsPlotComponentRef {
  fun getPlotHtml(): String
  fun toggleToolbar()
}