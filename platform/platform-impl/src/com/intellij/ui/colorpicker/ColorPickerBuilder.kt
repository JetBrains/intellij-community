/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.colorpicker

import com.intellij.ui.JBColor
import com.intellij.ui.picker.ColorListener
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.*

val PICKER_BACKGROUND_COLOR = JBColor(Color(252, 252, 252), Color(64, 64, 64))
val PICKER_TEXT_COLOR = Color(186, 186, 186)

const val PICKER_PREFERRED_WIDTH = 300
const val HORIZONTAL_MARGIN_TO_PICKER_BORDER = 14

private val PICKER_BORDER = JBUI.Borders.empty()

private const val SEPARATOR_HEIGHT = 5

/**
 * Builder class to help to create customized picker components depends on the requirement.
 */
class ColorPickerBuilder {

  private val componentsToBuild = mutableListOf<JComponent>()
  private val model = ColorPickerModel()
  private var originalColor: Color? = null
  private var requestFocusWhenDisplay = false
  private var focusCycleRoot = false
  private var focusedComponentIndex = -1
  private val actionMap = mutableMapOf<KeyStroke, Action>()
  private val colorListeners = mutableListOf<ColorListenerInfo>()

  fun setOriginalColor(originalColor: Color?) = apply { this.originalColor = originalColor }

  fun addSaturationBrightnessComponent() = apply { componentsToBuild.add(SaturationBrightnessComponent(model)) }

  @JvmOverloads
  fun addColorAdjustPanel(colorPipetteProvider: ColorPipetteProvider = GraphicalColorPipetteProvider()) = apply {
    componentsToBuild.add(ColorAdjustPanel(model, colorPipetteProvider))
  }

  fun addColorValuePanel() = apply { componentsToBuild.add(ColorValuePanel(model)) }

  /**
   * If both [okOperation] and [cancelOperation] are null, [IllegalArgumentException] will be raised.
   */
  fun addOperationPanel(okOperation: ((Color) -> Unit)?, cancelOperation: ((Color) -> Unit)?) = apply {
    componentsToBuild.add(OperationPanel(model, okOperation, cancelOperation))
    if (cancelOperation != null) {
      addKeyAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, true), object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) = cancelOperation.invoke(model.color)
      })
    }
  }

  /**
   * Add the custom components in to color picker.
   */
  fun addCustomComponent(provider: ColorPickerComponentProvider) = apply { componentsToBuild.add(provider.createComponent(model)) }

  fun addSeparator() = apply {
    val separator = JSeparator(JSeparator.HORIZONTAL)
    separator.border = JBUI.Borders.empty()
    separator.preferredSize = JBUI.size(PICKER_PREFERRED_WIDTH, SEPARATOR_HEIGHT)
    componentsToBuild.add(separator)
  }

  /**
   * Set if Color Picker should request focus when it is displayed.<br>
   *
   * The default value is **false**
   */
  fun focusWhenDisplay(focusWhenDisplay: Boolean) = apply { requestFocusWhenDisplay = focusWhenDisplay }

  /**
   * Set if Color Picker is the root of focus cycle.<br>
   * Set to true to makes the focus traversal inside this Color Picker only. This is useful when the Color Picker is used in an independent
   * window, e.g. a popup component or dialog.<br>
   *
   * The default value is **false**.
   *
   * @see Component.isFocusCycleRoot
   */
  fun setFocusCycleRoot(focusCycleRoot: Boolean) = apply { this.focusCycleRoot = focusCycleRoot }

  /**
   * When getting the focus, focus to the last added component.<br>
   * If this function is called multiple times, only the last time effects.<br>
   * By default, nothing is focused in ColorPicker.
   */
  fun withFocus() = apply { focusedComponentIndex = componentsToBuild.size - 1 }

  fun addKeyAction(keyStroke: KeyStroke, action: Action) = apply { actionMap[keyStroke] = action }

  fun addColorListener(colorListener: ColorListener) = addColorListener(colorListener, true)

  fun addColorListener(colorListener: ColorListener, invokeOnEveryColorChange: Boolean) = apply {
    colorListeners.add(ColorListenerInfo(colorListener, invokeOnEveryColorChange))
  }

  fun build(): LightCalloutPopup {
    if (componentsToBuild.isEmpty()) {
      throw IllegalStateException("The Color Picker should have at least one picking component.")
    }

    val width: Int = componentsToBuild.map { it.preferredSize.width }.max()!!
    val height = componentsToBuild.map { it.preferredSize.height }.sum()

    val defaultFocusComponent = componentsToBuild.getOrNull(focusedComponentIndex)

    val panel = object : JPanel() {
      override fun requestFocusInWindow() = defaultFocusComponent?.requestFocusInWindow() ?: false

      override fun addNotify() {
        super.addNotify()
        if (requestFocusWhenDisplay) {
          requestFocusInWindow()
        }
      }
    }
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = PICKER_BORDER
    panel.preferredSize = Dimension(width, height)
    panel.background = PICKER_BACKGROUND_COLOR

    val c = originalColor
    if (c != null) {
      model.setColor(c, null)
    }

    for (component in componentsToBuild) {
      panel.add(component)
    }

    panel.isFocusCycleRoot = focusCycleRoot
    panel.isFocusTraversalPolicyProvider = true
    panel.focusTraversalPolicy = MyFocusTraversalPolicy(defaultFocusComponent)

    actionMap.forEach { keyStroke, action ->
      val key = keyStroke.toString()
      panel.actionMap.put(key, action)
      panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(keyStroke, key)
    }

    colorListeners.forEach { model.addListener(it.colorListener, it.invokeOnEveryColorChange) }

    return LightCalloutPopup(panel,
                             closedCallback = { model.onClose() },
                             cancelCallBack = { model.onCancel() })
  }
}

private class MyFocusTraversalPolicy(val defaultComponent: Component?) : LayoutFocusTraversalPolicy() {
  override fun getDefaultComponent(aContainer: Container?): Component? = defaultComponent
}

private data class ColorListenerInfo(val colorListener: ColorListener, val invokeOnEveryColorChange: Boolean)
