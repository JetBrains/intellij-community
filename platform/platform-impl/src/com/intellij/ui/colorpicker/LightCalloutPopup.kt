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

import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent

/**
 * A popup balloon that appears on above a given location with an arrow pointing this location.
 *
 * The popup is automatically dismissed when the user clicks outside.
 */
class LightCalloutPopup(val content: JComponent,
  val closedCallback: (() -> Unit)? = null,
  val cancelCallBack: (() -> Unit)? = null,
  val beforeShownCallback: (() -> Unit)? = null
) {

  private var balloon: Balloon? = null

  /**
   * @param content The content in Popup Window
   * @param parentComponent The anchor component. Can be null.
   * @param location The position relates to [parentComponent]. If [parentComponent] is null, position will relate to
   *                 the top-left point of screen.
   * @param position The popup position, see [Balloon.Position]. The default value is [Balloon.Position.below].
   */
  @JvmOverloads
  fun show(
    parentComponent: JComponent? = null,
    location: Point,
    position: Balloon.Position = Balloon.Position.below
  ) {

    // Let's cancel any previous balloon shown by this instance of ScenePopup
    if (balloon != null) {
      cancel()
    }

    balloon = createPopup(content).apply {
      addListener(object : JBPopupListener {
        override fun beforeShown(event: LightweightWindowEvent) {
          beforeShownCallback?.invoke()
        }

        override fun onClosed(event: LightweightWindowEvent) {
          if (event.isOk) {
            closedCallback?.invoke()
          }
          else {
            cancelCallBack?.invoke()
          }
        }
      })

      val relativePoint = if (parentComponent != null) {
        RelativePoint(parentComponent, location)
      }
      else {
        RelativePoint(location)
      }
      show(relativePoint, position)
    }
  }

  fun close() {
    balloon?.hide(true)
  }

  fun cancel() {
    balloon?.hide(false)
  }

  private fun createPopup(component: JComponent) =
    JBPopupFactory.getInstance().createBalloonBuilder(component)
      .setFillColor(JBColor(0xfcfcfc, 0x313435))
      .setBorderColor(JBColor.border())
      .setBorderInsets(JBUI.insets(1))
      .setAnimationCycle(Registry.intValue("ide.tooltip.animationCycle"))
      .setShowCallout(true)
      .setPositionChangeYShift(2)
      .setHideOnKeyOutside(false)
      .setHideOnAction(false)
      .setBlockClicksThroughBalloon(true)
      .setRequestFocus(true)
      .setDialogMode(false)
      .createBalloon()
}

private val emptyRectangle = Rectangle(0, 0, 0, 0)

/**
 * Return true if there is enough space in the application window below [location]
 * in the [parentComponent] coordinates to show [content].
 */
fun canShowBelow(parentComponent: JComponent,
                 location: Point,
                 content: JComponent): Boolean {
  val relativePoint = RelativePoint(parentComponent, location)
  val windowBounds = UIUtil.getWindow(parentComponent)?.bounds ?: emptyRectangle
  return relativePoint.screenPoint.y + content.preferredSize.height < windowBounds.y + windowBounds.height
}