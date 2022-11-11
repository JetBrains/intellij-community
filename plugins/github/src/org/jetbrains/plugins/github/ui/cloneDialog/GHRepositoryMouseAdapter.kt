// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.ui.ListUtil
import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.UIUtil
import java.awt.Cursor
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JList

internal class GHRepositoryMouseAdapter(private val list: JList<*>) : MouseAdapter() {
  private fun getActionAt(e: MouseEvent): ActionListener? {
    val point = e.point
    val renderer = ListUtil.getDeepestRendererChildComponentAt(list, point)
    if (renderer !is SimpleColoredComponent) return null
    val tag = renderer.getFragmentTagAt(point.x)
    return if (tag is ActionListener) tag else null
  }

  override fun mouseMoved(e: MouseEvent) {
    val action = getActionAt(e)
    if (action != null) {
      UIUtil.setCursor(list, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
    }
    else {
      UIUtil.setCursor(list, Cursor.getDefaultCursor())
    }
  }

  override fun mouseClicked(e: MouseEvent) {
    getActionAt(e)?.actionPerformed(ActionEvent(e.source, e.id, "execute", e.modifiersEx))
  }
}