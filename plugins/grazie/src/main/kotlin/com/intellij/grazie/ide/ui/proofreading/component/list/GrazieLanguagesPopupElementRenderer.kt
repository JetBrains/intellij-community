// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.proofreading.component.list

import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.components.utils.configure
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList

class GrazieLanguagesPopupElementRenderer(list: ListPopupImpl) : PopupListElementRenderer<Lang>(list) {
  private lateinit var mySizeLabel: JLabel

  override fun createItemComponent(): JComponent {
    createLabel()
    createSizeLabel()

    val panel = panel(BorderLayout()) {
      add(myTextLabel, BorderLayout.CENTER)
      add(mySizeLabel, BorderLayout.EAST)
    }

    return layoutComponent(panel)
  }

  override fun customizeComponent(list: JList<out Lang>, lang: Lang, isSelected: Boolean) {
    @NlsSafe val size = lang.size.toString().takeUnless { lang.isAvailable() } ?: ""
    mySizeLabel.configure {
      text = size
      foreground = myTextLabel.foreground.takeIf { isSelected } ?: Color.GRAY
    }
  }

  private fun createSizeLabel() {
    mySizeLabel = JLabel().configure {
      border = JBUI.Borders.emptyLeft(5)
      foreground = JBColor.GRAY
    }
  }
}
