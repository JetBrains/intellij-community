// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ColoredText
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
class XStackFrameUiPresentationContainer : ColoredTextContainer {
  private val myFragments = mutableListOf<Pair<String, SimpleTextAttributes>>()
  private var myIcon: Icon? = null
  private var myTooltipText: @NlsContexts.Tooltip String? = null

  val fragments: List<Pair<String, SimpleTextAttributes>>
    get() = myFragments
  val icon: Icon?
    get() = myIcon
  val tooltipText: @NlsContexts.Tooltip String?
    get() = myTooltipText

  override fun append(fragment: @NlsContexts.Label String, attributes: SimpleTextAttributes, tag: Any?) {
    append(fragment, attributes)
  }

  override fun append(coloredText: ColoredText) {
    coloredText.fragments().forEach { fragment ->
      append(fragment.fragmentText(), fragment.fragmentAttributes())
    }
  }

  override fun setIcon(icon: Icon?) {
    this.myIcon = icon
  }

  override fun setToolTipText(text: @NlsContexts.Tooltip String?) {
    this.myTooltipText = text
  }

  override fun append(fragment: @NlsContexts.Label String, attributes: SimpleTextAttributes) {
    myFragments.add(fragment to attributes)
  }

  fun customizePresentation(container: ColoredTextContainer) {
    container.setIcon(myIcon)
    container.setToolTipText(myTooltipText)
    for ((fragment, attrs) in myFragments) {
      container.append(fragment, attrs)
    }
  }

  fun copy(): XStackFrameUiPresentationContainer {
    val copy = XStackFrameUiPresentationContainer()
    copy.myIcon = myIcon
    copy.myTooltipText = myTooltipText
    copy.myFragments.addAll(myFragments)
    return copy
  }
}
