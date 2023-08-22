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

import java.awt.Color
import javax.swing.Icon

/**
 * Interface for implementing the color pipette.
 *
 * @see ColorPipetteButton
 * @see GraphicalColorPipette
 */
interface ColorPipette {

  /**
   * Icon of [ColorPipetteButton] with normal status
   *
   * @see [javax.swing.JButton.setIcon]
   */
  val icon: Icon

  /**
   * Icon of [ColorPipetteButton] with hover status
   *
   * @see [javax.swing.JButton.setRolloverIcon]
   */
  val rolloverIcon: Icon

  /**
   * Icon of [ColorPipetteButton] with pressed status
   *
   * @see [javax.swing.JButton.setPressedIcon]
   */
  val pressedIcon: Icon

  /**
   * This function is called when associated [ColorPipetteButton] is clicked.
   *
   * @param callback Callback to handle the event after calling this function
   */
  fun pick(callback: Callback)

  interface Callback {

    /**
     * Called when the color is picked.
     */
    fun picked(pickedColor: Color)

    /**
     * Called when hovered color is changed but not really be picked.<br>
     * [updatedColor] is the color of current hovered pixel.
     */
    fun update(updatedColor: Color): Unit = Unit

    /**
     * Called when the picking is canceled.
     */
    fun cancel(): Unit = Unit
  }
}
