/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.picker.ColorListener
import com.intellij.ui.picker.ColorPickerPopupCloseListener
import java.awt.Color
import java.awt.Component

/**
 * @author Konstantin Bulenkov
 */
open class ColorChooserServiceImpl : ColorChooserService() {
  override fun showDialog(parent: Component,
                          @NlsContexts.DialogTitle caption: String?,
                          preselectedColor: Color?,
                          enableOpacity: Boolean,
                          listeners: List<ColorPickerListener>,
                          opacityInPercent: Boolean): Color? {
    return ColorPicker.showDialog(parent, caption, preselectedColor, enableOpacity, listeners, opacityInPercent)
  }

  override fun showDialog(project: Project?,
                          parent: Component,
                          @NlsContexts.DialogTitle caption: String?,
                          preselectedColor: Color?,
                          enableOpacity: Boolean,
                          listeners: List<ColorPickerListener>,
                          opacityInPercent: Boolean): Color? {
    return showDialog(parent, caption, preselectedColor, enableOpacity, listeners, opacityInPercent)
  }

  override fun showPopup(project: Project?,
                         currentColor: Color?,
                         editor: Editor?,
                         listener: ColorListener,
                         showAlpha: Boolean,
                         showAlphaAsPercent: Boolean,
                         popupCloseListener: ColorPickerPopupCloseListener?) {
    ColorPicker.showColorPickerPopup(project, currentColor, editor, listener, showAlpha, showAlphaAsPercent, popupCloseListener)
  }

  override fun showPopup(project: Project?,
                         currentColor: Color?,
                         listener: ColorListener,
                         location: RelativePoint?,
                         showAlpha: Boolean,
                         showAlphaAsPercent: Boolean,
                         popupCloseListener: ColorPickerPopupCloseListener?) {
    ColorPicker.showColorPickerPopup(project, currentColor, listener, location, showAlpha, showAlphaAsPercent, popupCloseListener)
  }

  override fun showColorPickerPopup(project: Project?,
                                    currentColor: Color?,
                                    listener: ColorListener,
                                    location: RelativePoint?,
                                    showAlpha: Boolean) {
    ColorPicker.showColorPickerPopup(project, currentColor, listener, location, showAlpha, false, null)
  }
}
