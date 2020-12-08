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
import com.intellij.ui.BalloonImpl
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent

/**
 * A popup balloon that appears on above a given location with an arrow pointing this location.
 *
 * The popup is automatically dismissed when the user clicks outside.
 * 
 * @param content The content in Popup Window
 */
class LightCalloutPopup(val content: JComponent,
  val closedCallback: (() -> Unit)? = null,
  val cancelCallBack: (() -> Unit)? = null,
  val beforeShownCallback: (() -> Unit)? = null
) {

  private var balloon: Balloon? = null

  fun getBalloon(): Balloon? = balloon

  /**
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

      if (this is BalloonImpl) {
        setHideListener {
          closedCallback?.invoke()
          hide()
        }
      }

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

  fun getPointerColor(showingPoint: RelativePoint, component: JComponent): Color? {
    val saturationBrightnessComponent = UIUtil.findComponentOfType(component, SaturationBrightnessComponent::class.java)
    if (saturationBrightnessComponent != null) {
      if (Registry.`is`("ide.color.picker.new.pipette") && saturationBrightnessComponent.pipetteMode) {
        return saturationBrightnessComponent.parent.background
      }
      val point = showingPoint.getPoint(saturationBrightnessComponent)
      val size = saturationBrightnessComponent.size
      val location = saturationBrightnessComponent.location
      val x1 = location.x
      val y1 = location.y
      val x2 = x1 + size.width
      val y2 = y1 + size.height
      val x = if (point.x < x1) x1 else if (point.x > x2) x2 else point.x
      val y = if (point.y < y1) y1 else if (point.y > y2) y2 else point.y
      if (y == y2) return null
      return saturationBrightnessComponent.getColorByPoint(Point(x,y))
    }
    return null
  }

  private fun createPopup(component: JComponent) =
    JBPopupFactory.getInstance().createBalloonBuilder(component)
      .setFillColor(PICKER_BACKGROUND_COLOR)
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