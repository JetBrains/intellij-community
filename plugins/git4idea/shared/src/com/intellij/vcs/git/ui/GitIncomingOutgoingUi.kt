// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.git.branch.GitInOutCountersInProject
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

  fun updateIncomingCommitLabel(label: JLabel, incomingOutgoingState: GitInOutCountersInProject) {
    val isEmpty = incomingOutgoingState == GitInOutCountersInProject.EMPTY
    val totalIncoming = incomingOutgoingState.totalIncoming()

    label.isVisible = !isEmpty && (totalIncoming > 0 || incomingOutgoingState.hasUnfetched())
    if (!label.isVisible) return

    label.text = if (totalIncoming > 0) shrinkTo99(totalIncoming) else ""
  }

  fun updateOutgoingCommitLabel(label: JLabel, state: GitInOutCountersInProject) {
    val isEmpty = state == GitInOutCountersInProject.EMPTY
    val totalOutgoing = state.totalOutgoing()

    label.isVisible = !isEmpty && totalOutgoing > 0
    if (!label.isVisible) return

    label.text = if (totalOutgoing > 0) shrinkTo99(totalOutgoing) else ""
  }

  private fun shrinkTo99(commits: Int): @NlsSafe String {
    if (commits > 99) return "99+"
    return commits.toString()
  }

  private val ICON_TEXT_GAP
    get() = JBUI.scale(1)
}