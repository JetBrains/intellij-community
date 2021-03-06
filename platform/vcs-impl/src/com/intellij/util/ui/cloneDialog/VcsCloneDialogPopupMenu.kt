// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.cloneDialog

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.ColorUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.GridBagLayout
import javax.swing.*


sealed class AccountMenuItem(val showSeparatorAbove: Boolean) {

  class Account(val title: @Nls String,
                val info: @Nls String,
                val icon: Icon,
                val actions: List<AccountMenuItem> = emptyList(),
                showSeparatorAbove: Boolean = false
  ) : AccountMenuItem(showSeparatorAbove)

  class Action(val text: @Nls String,
               val runnable: () -> Unit,
               val rightIcon: Icon = EmptyIcon.create(AllIcons.Ide.External_link_arrow),
               showSeparatorAbove: Boolean = false
  ) : AccountMenuItem(showSeparatorAbove)

}

class AccountMenuPopupStep(items: List<AccountMenuItem>) : BaseListPopupStep<AccountMenuItem>(null, items) {
  override fun hasSubstep(selectedValue: AccountMenuItem?): Boolean {
    return selectedValue is AccountMenuItem.Account && selectedValue.actions.isNotEmpty()
  }

  override fun onChosen(selectedValue: AccountMenuItem, finalChoice: Boolean): PopupStep<*>? = when (selectedValue) {
    is AccountMenuItem.Action -> doFinalStep(selectedValue.runnable)
    is AccountMenuItem.Account -> if (selectedValue.actions.isEmpty()) null else AccountMenuPopupStep(selectedValue.actions)
  }

  override fun getBackgroundFor(value: AccountMenuItem?) = UIUtil.getPanelBackground()
}

class AccountsMenuListPopup(
  project: Project? = null,
  accountMenuPopupStep: AccountMenuPopupStep,
  parent: WizardPopup? = null,
  parentObject: Any? = null
) : ListPopupImpl(project,
                  parent,
                  accountMenuPopupStep,
                  parentObject
) {
  override fun getListElementRenderer() = AccountMenuItemRenderer()

  override fun createPopup(parent: WizardPopup?, step: PopupStep<*>?, parentValue: Any?) = AccountsMenuListPopup(
    parent?.project,
    step as AccountMenuPopupStep,
    parent,
    parentValue)
}

class AccountMenuItemRenderer : ListCellRenderer<AccountMenuItem> {
  private val leftInset = 12
  private val innerInset = 8
  private val emptyMenuRightArrowIcon = EmptyIcon.create(AllIcons.General.ArrowRight)
  private val separatorBorder = JBUI.Borders.customLine(JBUI.CurrentTheme.Popup.separatorColor(), 1, 0, 0, 0)

  private val listSelectionBackground = UIUtil.getListSelectionBackground(true)

  private val accountRenderer = AccountItemRenderer()
  private val actionRenderer = ActionItemRenderer()

  override fun getListCellRendererComponent(list: JList<out AccountMenuItem>?,
                                            value: AccountMenuItem,
                                            index: Int,
                                            selected: Boolean,
                                            focused: Boolean): Component {
    val renderer = when (value) {
      is AccountMenuItem.Account -> accountRenderer.getListCellRendererComponent(null, value, index, selected, focused)
      is AccountMenuItem.Action -> actionRenderer.getListCellRendererComponent(null, value, index, selected, focused)
    }
    UIUtil.setBackgroundRecursively(renderer, if (selected) listSelectionBackground else UIUtil.getPanelBackground())
    renderer.border = if (value.showSeparatorAbove) separatorBorder else null
    return renderer
  }

  private inner class AccountItemRenderer : JPanel(GridBagLayout()), ListCellRenderer<AccountMenuItem.Account> {
    private val listSelectionForeground = UIUtil.getListSelectionForeground(true)

    val avatarLabel = JLabel()
    val titleComponent = JLabel().apply {
      font = JBUI.Fonts.label().asBold()
    }
    val infoComponent = JLabel().apply {
      font = JBUI.Fonts.smallFont()
    }
    val nextStepIconLabel = JLabel()

    init {
      val insets = JBUI.insets(innerInset, leftInset, innerInset, innerInset)
      var gbc = GridBag().setDefaultAnchor(GridBag.WEST)

      gbc = gbc.nextLine().next() // 1, |
        .weightx(0.0).fillCellVertically().insets(insets).coverColumn()
      add(avatarLabel, gbc)

      gbc = gbc.next() // 2, 1
        .weightx(1.0).weighty(0.5).fillCellHorizontally().anchor(GridBag.LAST_LINE_START)
      add(titleComponent, gbc)

      gbc = gbc.next() // 3, |
        .weightx(0.0).insets(insets).coverColumn()
      add(nextStepIconLabel, gbc)

      gbc = gbc.nextLine().next().next() // 2, 2
        .weightx(1.0).weighty(0.5).fillCellHorizontally().anchor(GridBag.FIRST_LINE_START)
      add(infoComponent, gbc)
    }

    override fun getListCellRendererComponent(list: JList<out AccountMenuItem.Account>?,
                                              value: AccountMenuItem.Account,
                                              index: Int,
                                              selected: Boolean,
                                              focused: Boolean): JComponent {
      avatarLabel.icon = value.icon

      titleComponent.apply {
        text = value.title
        foreground = if (selected) listSelectionForeground else SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES.fgColor
      }

      infoComponent.apply {
        text = value.info
        foreground = if (selected) listSelectionForeground else SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES.fgColor
      }

      nextStepIconLabel.apply {
        icon = when {
          value.actions.isEmpty() -> emptyMenuRightArrowIcon
          selected && ColorUtil.isDark(listSelectionBackground) -> AllIcons.Icons.Ide.NextStepInverted
          else -> AllIcons.Icons.Ide.NextStep
        }
      }
      return this
    }
  }

  private inner class ActionItemRenderer : JPanel(GridBagLayout()), ListCellRenderer<AccountMenuItem.Action> {

    val actionTextLabel = JLabel()
    val rightIconLabel = JLabel()

    init {
      val topBottom = 3
      val insets = JBUI.insets(topBottom, leftInset, topBottom, 0)

      var gbc = GridBag().setDefaultAnchor(GridBag.WEST)
      gbc = gbc.nextLine().next().insets(insets)
      add(actionTextLabel, gbc)
      gbc = gbc.next()
      add(rightIconLabel, gbc)
      gbc = gbc.next().fillCellHorizontally().weightx(1.0).anchor(GridBag.EAST)
        .insets(JBUI.insets(topBottom, leftInset, topBottom, innerInset))
      add(JLabel(emptyMenuRightArrowIcon), gbc)
    }

    override fun getListCellRendererComponent(list: JList<out AccountMenuItem.Action>?,
                                              value: AccountMenuItem.Action,
                                              index: Int,
                                              selected: Boolean,
                                              focused: Boolean): JComponent {
      actionTextLabel.text = value.text
      rightIconLabel.icon = value.rightIcon
      actionTextLabel.foreground = UIUtil.getListForeground(selected, true)
      return this
    }
  }
}
