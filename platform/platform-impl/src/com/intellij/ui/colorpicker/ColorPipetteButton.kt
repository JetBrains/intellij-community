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

import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.JButton

@ApiStatus.Internal
class ColorPipetteButton(private val colorPickerModel: ColorPickerModel, private val pipette: ColorPipette) : JButton() {

  init {
    isRolloverEnabled = true

    icon = pipette.icon
    rolloverIcon = pipette.rolloverIcon
    pressedIcon = pipette.pressedIcon

    addActionListener { pipette.pick(MyCallback(colorPickerModel)) }
  }

  var currentState: PipetteState = PipetteState.NONE

  enum class PipetteState {PICKED, UPDATING, NONE}

  private inner class MyCallback(val model: ColorPickerModel): ColorPipette.Callback {

    private val originalColor = model.color

    override fun picked(pickedColor: Color) {
      currentState = PipetteState.PICKED
      model.setColor(pickedColor, this@ColorPipetteButton)
      model.firePipettePicked(pickedColor)
      currentState = PipetteState.NONE
    }

    override fun update(updatedColor: Color) {
      currentState = PipetteState.UPDATING
      model.setColor(updatedColor, this@ColorPipetteButton)
      model.firePipetteUpdated(updatedColor)
    }

    override fun cancel() {
      currentState = PipetteState.PICKED
      model.setColor(originalColor, this@ColorPipetteButton)
      model.firePipetteCancelled()
      currentState = PipetteState.NONE
    }
  }
}
