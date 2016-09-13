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
import com.intellij.util.SmartList
import com.intellij.util.ui.UIUtil
import java.awt.event.ActionEvent
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

class Row(private val buttonGroup: ButtonGroup?, internal val labeled: Boolean = false, internal val spanned: Boolean = false) {
  var rightIndex = Int.MAX_VALUE
  val components = SmartList<JComponent>()

  fun label(text: String, style: UIUtil.ComponentStyle? = null, fontColor: UIUtil.FontColor? = null, bold: Boolean = false) {
    Label(text, style, fontColor, bold)()
  }

  fun link(text: String, style: UIUtil.ComponentStyle? = null, action: () -> Unit) {
    val result = Link(text, action = action)
    style?.let { UIUtil.applyStyle(it, result) }
    result()
  }

  fun button(text: String, actionListener: (event: ActionEvent) -> Unit) {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    button()
  }

  operator fun JComponent.invoke() {
    if (buttonGroup != null && this is JButton) {
      buttonGroup.add(this)
    }
    this@Row.components.add(this)
  }

  inline fun right(init: Row.() -> Unit) {
    if (rightIndex != Int.MAX_VALUE) {
      throw IllegalStateException("right allowed only once")
    }
    rightIndex = components.size

    init()
  }

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