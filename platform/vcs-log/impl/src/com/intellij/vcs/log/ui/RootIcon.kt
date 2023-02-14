// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.ui.ExperimentalUI
import com.intellij.ui.SizedIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.LafIconLookup
import java.awt.Color
import java.awt.Component
import java.awt.Graphics

object RootIcon {
  @JvmStatic
  fun create(
    color: Color,
  ): ColorIcon {
    val size = iconSize
    return ColorIcon(size, size, size, size, color, false, arcSize)
  }

  @JvmStatic
  fun createAndScale(color: Color): ColorIcon = JBUIScale.scaleIcon(create(color))

  @JvmStatic
  fun createAndScaleCheckbox(color: Color): CheckboxColorIcon {
    return JBUIScale.scaleIcon(CheckboxColorIcon(iconSize, color, arcSize))
  }

  private const val iconSize = 14
  private const val checkMarkSize = 12

  private val arcSize
    get() = if (ExperimentalUI.isNewUI()) 4 else 0


  class CheckboxColorIcon(size: Int, color: Color, arc: Int) : ColorIcon(size, size, size, size, color, false, arc) {
    private var mySelected = false
    private var mySizedIcon: SizedIcon

    init {
      val icon = if (ExperimentalUI.isNewUI()) {
        IconUtil.resizeSquared(LafIconLookup.getIcon("checkmark", true), checkMarkSize)
      }
      else {
        PlatformIcons.CHECK_ICON_SMALL
      }
      mySizedIcon = SizedIcon(icon, checkMarkSize, checkMarkSize)
    }

    fun prepare(selected: Boolean) {
      mySelected = selected
    }

    override fun withIconPreScaled(preScaled: Boolean): CheckboxColorIcon {
      mySizedIcon = mySizedIcon.withIconPreScaled(preScaled) as SizedIcon
      return super.withIconPreScaled(preScaled) as CheckboxColorIcon
    }

    override fun paintIcon(component: Component, g: Graphics, i: Int, j: Int) {
      super.paintIcon(component, g, i, j)

      if (mySelected) {
        val offset = (iconWidth - mySizedIcon.iconWidth) / 2
        mySizedIcon.paintIcon(component, g, i + offset, j + offset)
      }
    }
  }
}