// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge.dialog

import com.intellij.icons.AllIcons.Actions
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class OptionListCellRenderer<T>(private val optionInfoProvider: (T) -> OptionInfo<T>,
                                private val optionSelectedPredicate: (T) -> Boolean,
                                private val optionSelectablePredicate: (T) -> Boolean) : ListCellRenderer<T> {

  private val iconLabel = JLabel()
  private val optionLabel = JLabel()
  private val optionDescriptionLabel = JLabel()

  private val panel = createPanel()

  override fun getListCellRendererComponent(list: JList<out T>?,
                                            value: T,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val optionSelected = optionSelectedPredicate(value)
    val optionSelectable = optionSelectablePredicate(value)

    panel.apply {
      background = UIUtil.getListBackground(isSelected, true)
      border = if (isSelected && isEnabled) border else null
    }

    iconLabel.icon = if (optionSelected) Actions.Checked else ICON_UNCHECKED

    optionLabel.apply {
      text = optionInfoProvider(value).flag
      foreground = when {
        isSelected -> list?.selectionForeground
        optionSelectable -> list?.foreground
        else -> JBUI.CurrentTheme.Label.disabledForeground()
      }
    }

    optionDescriptionLabel.apply {
      text = optionInfoProvider(value).description
      foreground = if (isSelected)
        list?.selectionForeground
      else
        JBUI.CurrentTheme.Label.disabledForeground()
    }

    return panel
  }

  private fun createPanel() = JPanel().apply {
    layout = FlowLayout().apply { alignment = FlowLayout.LEFT }
    add(iconLabel)
    add(optionLabel)
    add(optionDescriptionLabel)
  }

  companion object {
    private val ICON_UNCHECKED = JBUIScale.scaleIcon(EmptyIcon.create(12))
  }
}