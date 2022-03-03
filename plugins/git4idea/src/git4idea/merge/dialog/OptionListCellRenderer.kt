// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.merge.dialog

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.LafIconLookup
import javax.swing.JComponent
import javax.swing.JList

class OptionListCellRenderer<T>(
  private val optionInfoProvider: (T) -> OptionInfo<T>,
  private val optionSelectedPredicate: (T) -> Boolean,
  private val optionSelectablePredicate: (T) -> Boolean,
  private val optionLongestDescription: String,
  listPopup: ListPopupImpl
) : PopupListElementRenderer<T>(listPopup) {

  private lateinit var optionComponent: SimpleColoredComponent

  override fun customizeComponent(list: JList<out T>, value: T, isSelected: Boolean) {
    super.customizeComponent(list, value, isSelected)

    val optionInfo: OptionInfo<T> = optionInfoProvider(value)
    val optionSelected = optionSelectedPredicate(value)
    val optionSelectable = optionSelectablePredicate(value)

    val descriptionAttributes = when {
      isSelected -> SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES
      optionSelectable -> SimpleTextAttributes.REGULAR_ATTRIBUTES
      else -> SimpleTextAttributes.GRAYED_ATTRIBUTES
    }
    val flagAttributes = if (isSelected) SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES else SimpleTextAttributes.GRAYED_ATTRIBUTES
    val checkmarkIcon = LafIconLookup.getIcon("checkmark", isSelected, false)

    optionComponent.apply {
      clear()
      append(optionInfo.description, descriptionAttributes)
      appendTextPadding(getFontMetrics(font).stringWidth(optionLongestDescription) + JBUIScale.scale(OFFSET_BETWEEN_OPTIONS_TEXT))
      append(optionInfo.flag, flagAttributes)

      icon = if (optionSelected) checkmarkIcon else EmptyIcon.create(checkmarkIcon)
    }
  }

  override fun createItemComponent(): JComponent {
    createLabel()
    optionComponent = SimpleColoredComponent().apply {
      ipad = JBInsets.emptyInsets()
    }
    return layoutComponent(optionComponent)
  }

  companion object {
    private const val OFFSET_BETWEEN_OPTIONS_TEXT = 50
  }
}