// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview

import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

object ReturnToListComponent {
  fun createReturnToListSideComponent(@Nls text: String, onClick: () -> Unit): JComponent = BorderLayoutPanel()
    .addToRight(LinkLabel<Any>("${UIUtil.leftArrow()} $text", null) { _, _ ->
      onClick()
    }.apply {
      border = JBUI.Borders.emptyRight(8)
    })
    .andTransparent()
    .withBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM))
}