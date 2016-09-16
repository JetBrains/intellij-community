/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.layout

import com.intellij.BundleBase
import com.intellij.ui.components.Label
import com.intellij.ui.components.Link
import com.intellij.ui.components.Panel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.ComponentStyle
import com.intellij.util.ui.UIUtil.FontColor
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

abstract class Row() {
  fun label(text: String, gapLeft: Int = 0, style: ComponentStyle? = null, fontColor: FontColor? = null, bold: Boolean = false) {
    Label(text, style, fontColor, bold)(gapLeft = gapLeft)
  }

  fun link(text: String, style: ComponentStyle? = null, action: () -> Unit) {
    val result = Link(text, action = action)
    style?.let { UIUtil.applyStyle(it, result) }
    result()
  }

  fun button(text: String, actionListener: (event: ActionEvent) -> Unit) {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    button()
  }

  fun hint(text: String) {
    label(text, style = ComponentStyle.SMALL, fontColor = FontColor.BRIGHTER, gapLeft = 3 * HORIZONTAL_GAP)
  }

  fun panel(title: String, wrappedComponent: Component, vararg constraints: CCFlags) {
    val panel = Panel(title)
    panel.add(wrappedComponent)
    panel(*constraints)
  }

  abstract operator fun JComponent.invoke(vararg constraints: CCFlags, gapLeft: Int = 0)

  inline fun right(init: Row.() -> Unit) {
    alignRight()
    init()
  }

  protected abstract fun alignRight()

  @Deprecated(message = "Nested row is prohibited", level = DeprecationLevel.ERROR)
  fun row(label: String, init: Row.() -> Unit) {
  }

  @Deprecated(message = "Nested row is prohibited", level = DeprecationLevel.ERROR)
  fun row(label: JLabel? = null, init: Row.() -> Unit) {
  }

  @Deprecated(message = "Nested noteRow is prohibited", level = DeprecationLevel.ERROR)
  fun noteRow(text: String) {
  }
}