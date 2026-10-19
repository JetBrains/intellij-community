package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

class JcefOffScreenViewComponent(data: ComponentData) : UiComponent(data) {
  private val browser by lazy { driver.cast(component, JcefOffScreenViewComponentRef::class) }

  val htmlSource: String
    get() = browser.getSource()

  /**
   * Whether the view shows a Mermaid diagram that `@jupyterlab/mermaid` rendered: a `jp-RenderedMermaid` element with an SVG.
   * A failed render adds `jp-mod-warning`.
   */
  fun hasRenderedMermaid(): Boolean = htmlSource.let {
    it.contains("jp-RenderedMermaid") && (it.contains("<svg") || it.contains("image/svg+xml")) && !it.contains("jp-mod-warning")
  }
}

@Remote("com.intellij.jupyter.core.jupyter.editor.outputs.webOutputs.component.JcefOffScreenViewComponent",
        plugin = "intellij.jupyter/intellij.jupyter.core")
interface JcefOffScreenViewComponentRef {
  fun getSource(): String
}