// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.ui

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.DvcsImplIcons
import org.jetbrains.annotations.ApiStatus
import javax.swing.JLabel

@ApiStatus.Internal
object GitIncomingOutgoingUi {
  private val INCOMING_FOREGROUND: JBColor
    get() = JBColor(ColorUtil.fromHex("#3574F0"), ColorUtil.fromHex("#548AF7"))

  private val OUTGOING_FOREGROUND: JBColor
    get() = JBColor(ColorUtil.fromHex("#369650"), ColorUtil.fromHex("#57965C"))

  fun createIncomingLabel(): JLabel = JBLabel().apply {
    icon = DvcsImplIcons.Incoming
    iconTextGap = ICON_TEXT_GAP
    componentStyle = UIUtil.ComponentStyle.SMALL
    foreground = INCOMING_FOREGROUND
  }

  fun createOutgoingLabel(): JLabel = JBLabel().apply {
    icon = DvcsImplIcons.Outgoing
    iconTextGap = ICON_TEXT_GAP
    componentStyle = UIUtil.ComponentStyle.SMALL
    foreground = OUTGOING_FOREGROUND
  }

  private val ICON_TEXT_GAP
    get() = JBUI.scale(1)
}