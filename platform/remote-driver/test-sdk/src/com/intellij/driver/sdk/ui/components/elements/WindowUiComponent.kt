package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.Window
import java.awt.Rectangle

open class WindowUiComponent(data: ComponentData) : UiComponent(data) {
  private val windowComponent get() = driver.cast(component, Window::class)

  fun isFocused(): Boolean = callOnEdt { windowComponent.isFocused() }

  fun dispose() = callOnEdt { windowComponent.dispose() }

  open fun toFront() = callOnEdt { windowComponent.toFront() }

  fun setBounds(x: Int, y: Int, width: Int, height: Int) = callOnEdt { windowComponent.setBounds(x, y, width, height) }

  fun setBounds(bounds: Rectangle) = setBounds(bounds.x, bounds.y, bounds.width, bounds.height)

  private fun <T> callOnEdt(block: () -> T) = driver.withContext(OnDispatcher.EDT) { block() }
}