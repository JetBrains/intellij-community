// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.pullrequest.ui.WrapLayout
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.equalVetoingObservable
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel

internal abstract class LabeledListPanelHandle<T>(emptyText: String, notEmptyText: String) {
  val label = JLabel().apply {
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP + 2, 0, UIUtil.DEFAULT_VGAP + 2, UIUtil.DEFAULT_HGAP / 2)
  }
  val panel = NonOpaquePanel(WrapLayout(FlowLayout.LEADING, 0, 0))

  var list: List<T>? by equalVetoingObservable<List<T>?>(null) { newList ->
    label.text = newList?.let { if (it.isEmpty()) emptyText else notEmptyText }
    label.isVisible = newList != null

    panel.removeAll()
    panel.isVisible = newList != null
    if (newList != null) for (item in newList) {
      panel.add(getListItemComponent(item))
    }
  }

  abstract fun getListItemComponent(item: T): JComponent

  companion object {
    inline fun <T> create(emptyText: String, notEmptyText: String, crossinline componentProvider: (T) -> JComponent) =
      object : LabeledListPanelHandle<T>(emptyText, notEmptyText) {
        override fun getListItemComponent(item: T) = componentProvider(item)
      }
  }
}