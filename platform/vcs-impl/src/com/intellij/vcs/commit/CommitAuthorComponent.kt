// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.util.text.StringUtil.escapeXmlEntities
import com.intellij.openapi.util.text.StringUtil.unescapeXmlEntities
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI.Borders.emptyRight
import com.intellij.util.ui.UIUtil.getInactiveTextColor
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.util.VcsUserUtil.getShortPresentation
import kotlin.properties.Delegates.observable

class CommitAuthorComponent : NonOpaquePanel(HorizontalLayout(0)) {
  private val viewer: VcsUserViewer = VcsUserViewer()

  var commitAuthor by observable<VcsUser?>(null) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable

    isVisible = newValue != null
    viewer.user = newValue
  }

  init {
    isVisible = false

    add(JBLabel("by").apply {
      foreground = getInactiveTextColor()
      border = emptyRight(4)
    })
    add(viewer)
  }
}

private class VcsUserViewer : LinkLabel<Any>(null, null) {
  var user by observable<VcsUser?>(null) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable

    text = newValue?.let { getShortPresentation(it) }
    toolTipText = newValue?.let { escapeXmlEntities(it.toString()) }
  }

  override fun getStatusBarText(): String = unescapeXmlEntities(super.getStatusBarText())
}