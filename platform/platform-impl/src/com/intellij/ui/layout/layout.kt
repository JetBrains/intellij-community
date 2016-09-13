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

import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.Label
import com.intellij.ui.components.RadioButton
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.noteComponent
import com.intellij.ui.layout.LCFlags.*
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.ComponentStyle
import com.intellij.util.ui.UIUtil.FontColor
import net.miginfocom.layout.BoundSize
import net.miginfocom.layout.CC
import net.miginfocom.layout.UnitValue
import java.awt.Component

inline fun panel(title: String? = null, init: LayoutBuilder.() -> Unit) = createPanel2(title, init)

inline fun verticalPanel(init: Panel.() -> Unit) = createUnsafePanel(layoutConstraints = arrayOf(noGrid, flowY, fillX), init = init)

fun Panel.label(text: String,
                 vararg constraints: CCFlags,
                 style: ComponentStyle? = null,
                 fontColor: FontColor? = null,
                 bold: Boolean = false,
                 gapLeft: Int = 0,
                 gapBottom: Int = 0,
                 gapAfter: Int = 0,
                 split: Int = -1) {
  add(Label(text, style, fontColor, bold), createComponentConstraints(constraints, gapLeft = gapLeft, gapBottom = gapBottom, gapAfter = gapAfter, split = split))
}

fun Panel.link(text: String, vararg constraints: CCFlags, style: ComponentStyle? = null, action: () -> Unit) {
  val result = LinkLabel.create(text, action)
  style?.let { UIUtil.applyStyle(it, result) }
  add(result, constraints.create())
}

fun Panel.link(text: String, url: String, vararg constraints: CCFlags, style: ComponentStyle? = null) {
  val result = LinkLabel.create(text, { BrowserUtil.browse(url) })
  style?.let { UIUtil.applyStyle(it, result) }
  add(result, constraints.create())
}
fun Panel.hint(text: String, vararg constraints: CCFlags) {
  label(text, style = ComponentStyle.SMALL, fontColor = FontColor.BRIGHTER, constraints = *constraints, gapLeft = 3 * GAP)
}

/**
 * Hyperlinks are supported (`<a href=""></a>`), new lines and <br> are not supported.
 */
fun Panel.note(text: String, vararg constraints: CCFlags) {
  add(noteComponent(text), createComponentConstraints(constraints, gapTop = GAP))
}

fun Panel.radioButton(text: String, vararg constraints: CCFlags) {
  add(RadioButton(text), constraints.create())
}

fun Panel.panel(title: String, wrappedComponent: Component, vararg constraints: CCFlags) {
  val panel = com.intellij.ui.components.Panel(title)
  panel.add(wrappedComponent)
  add(panel, constraints.create())
}

internal fun gapToBoundSize(value: Int, isHorizontal: Boolean): BoundSize {
  val unitValue = UnitValue(value.toFloat(), "", isHorizontal, UnitValue.STATIC, null)
  return BoundSize(unitValue, unitValue, null, false, null)
}

private fun createComponentConstraints(constraints: Array<out CCFlags>,
                                       gapLeft: Int = 0,
                                       gapAfter: Int = 0,
                                       gapTop: Int = 0,
                                       gapBottom: Int = 0,
                                       split: Int = -1): CC? {
  var _cc = constraints.create()
  fun cc(): CC {
    if (_cc == null) {
      _cc = CC()
    }
    return _cc!!
  }

  if (gapLeft != 0) {
    cc().horizontal.gapBefore = gapToBoundSize(gapLeft, true)
  }
  if (gapAfter != 0) {
    cc().horizontal.gapAfter = gapToBoundSize(gapAfter, true)
  }

  if (gapTop != 0) {
    cc().vertical.gapBefore = gapToBoundSize(gapTop, false)
  }
  if (gapBottom != 0) {
    cc().vertical.gapAfter = gapToBoundSize(gapBottom, false)
  }

  if (split != -1) {
    cc().split = split
  }
  return _cc
}