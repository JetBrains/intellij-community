// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.util.ui.UIUtil
import java.awt.FontMetrics
import javax.swing.JComponent

internal open class DefaultPartTitle(open var prefix: String = " ", open var suffix: String = ""): BaseTitlePart {
  private var shortTextWidth: Int = 0
  private var longTextWidth: Int = 0

  protected var state = TitlePart.State.LONG

  override var active: Boolean = true

  override var longText: String = ""
    set(value) {
      if (value == field) return
      field = value
      shortText = value
    }

  override var shortText: String = ""

  override fun getLong(): String {
    return if (!active || longText.isEmpty()) "" else "$prefix$longText$suffix"
  }

  override fun getShort(): String {
    return if (!active || shortText.isEmpty()) "" else "$prefix$shortText$suffix"
  }

  override val longWidth: Int get() = longTextWidth

  override val shortWidth: Int get() = shortTextWidth

  override val toolTipPart: String
    get() = if (longText.isEmpty()) "" else "$prefix$longText$suffix"

  override fun refresh(label: JComponent, fm: FontMetrics) {
    longTextWidth = if (longText.isEmpty() || !active) 0 else UIUtil.computeStringWidth(label, fm, "$prefix$longText$suffix")
    shortTextWidth = if (shortText.isEmpty() || !active) 0 else UIUtil.computeStringWidth(label, fm, "$prefix$shortText$suffix")
  }
}