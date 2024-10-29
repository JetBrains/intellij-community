// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.cloneDialog

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.ColorUtil
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.Color
import java.awt.Component
import java.awt.GridBagLayout
import java.awt.Point
import javax.swing.*

sealed class AccountMenuItem(val showSeparatorAbove: Boolean) {
  class Account(
    @Nls val title: String,
    @Nls val info: String,
    val icon: Icon,
    val actions: List<AccountMenuItem> = emptyList(),
    showSeparatorAbove: Boolean = false
  ) : AccountMenuItem(showSeparatorAbove)

  class Action(
    @Nls val text: String,
    val runnable: () -> Unit,
    val rightIcon: Icon = EmptyIcon.create(AllIcons.Ide.External_link_arrow),
    showSeparatorAbove: Boolean = false
  ) : AccountMenuItem(showSeparatorAbove)

  class Group(
    @Nls val text: String,
    val actions: List<AccountMenuItem> = emptyList(),
    showSeparatorAbove: Boolean = false
  ) : AccountMenuItem(showSeparatorAbove)
}

class AccountMenuPopupStep(items: List<AccountMenuItem>) : BaseListPopupStep<AccountMenuItem>(null, items) {
  override fun hasSubstep(selectedValue: AccountMenuItem?): Boolean {
    return selectedValue is AccountMenuItem.Account && selectedValue.actions.size > 1
  }

  override fun onChosen(selectedValue: AccountMenuItem, finalChoice: Boolean): PopupStep<*>? = when (selectedValue) {
    is AccountMenuItem.Action -> doFinalStep(selectedValue.runnable)
    is AccountMenuItem.Account -> when {
      selectedValue.actions.isEmpty() -> null
      selectedValue.actions.size == 1 -> doFinalStep((selectedValue.actions.first() as AccountMenuItem.Action).runnable)
      else -> AccountMenuPopupStep(selectedValue.actions)
    }
    is AccountMenuItem.Group -> AccountMenuPopupStep(selectedValue.actions)
  }

  override fun getBackgroundFor(value: AccountMenuItem?): Color = UIUtil.getListBackground()
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
  override fun getListElementRenderer(): AccountMenuItemRenderer = AccountMenuItemRenderer()

  override fun createPopup(parent: WizardPopup?, step: PopupStep<*>?, parentValue: Any?): AccountsMenuListPopup = AccountsMenuListPopup(
    parent?.project,
    step as AccountMenuPopupStep,
    parent,
    parentValue)

  override fun showUnderneathOf(aComponent: Component) {
    show(RelativePoint(aComponent, Point(-this.component.preferredSize.width + aComponent.width, aComponent.height)))
  }
}

@ApiStatus.Internal
class AccountMenuItemRenderer : ListCellRenderer<AccountMenuItem> {
  private val leftInset = 12
  private val innerInset = 8
  private val emptyMenuRightArrowIcon = EmptyIcon.create(AllIcons.General.ArrowRight)
  private val separatorBorder = JBUI.Borders.customLine(JBUI.CurrentTheme.Popup.separatorColor(), 1, 0, 0, 0)

  private val listSelectionBackground = UIUtil.getListSelectionBackground(true)

  private val accountRenderer = AccountItemRenderer().apply {
    isOpaque = false
  }
  private val actionRenderer = ActionItemRenderer().apply {
    isOpaque = false
  }
  private val groupRenderer = GroupItemRenderer().apply {
    isOpaque = false
  }

  override fun getListCellRendererComponent(list: JList<out AccountMenuItem>?,
                                            value: AccountMenuItem,
                                            index: Int,
                                            selected: Boolean,
                                            focused: Boolean): Component {
    val renderer = when (value) {
      is AccountMenuItem.Account -> accountRenderer.getListCellRendererComponent(null, value, index, selected, focused)
      is AccountMenuItem.Action -> actionRenderer.getListCellRendererComponent(null, value, index, selected, focused)
      is AccountMenuItem.Group -> groupRenderer.getListCellRendererComponent(null, value, index, selected, focused)
    }
    UIUtil.setBackgroundRecursively(renderer, if (selected) listSelectionBackground else UIUtil.getListBackground())
    renderer.border = if (value.showSeparatorAbove) separatorBorder else null
    return renderer
  }

  private inner class AccountItemRenderer : JPanel(GridBagLayout()), ListCellRenderer<AccountMenuItem.Account> {
    private val listSelectionForeground = NamedColorUtil.getListSelectionForeground(true)

    val avatarLabel = JLabel()
    val titleComponent = SimpleColoredComponent().apply {
      isOpaque = false
    }
    val link = SimpleColoredComponent().apply {
      isOpaque = false
    }
    val nextStepIconLabel = SimpleColoredComponent().apply {
      isOpaque = false
    }

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
      add(link, gbc)
    }

    override fun getListCellRendererComponent(list: JList<out AccountMenuItem.Account>?,
                                              value: AccountMenuItem.Account,
                                              index: Int,
                                              selected: Boolean,
                                              focused: Boolean): JComponent {
      avatarLabel.icon = value.icon

      titleComponent.apply {
        clear()
        val foreground = if (selected) listSelectionForeground else SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES.fgColor
        append(value.title, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, foreground))
      }

      link.apply {
        clear()
        val foreground = if (selected) listSelectionForeground else UIUtil.getContextHelpForeground()
        append(value.info, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foreground))
      }

      nextStepIconLabel.apply {
        icon = when {
          value.actions.size < 2 -> emptyMenuRightArrowIcon
          selected && ColorUtil.isDark(listSelectionBackground) -> AllIcons.Icons.Ide.NextStepInverted
          else -> AllIcons.Icons.Ide.NextStep
        }
      }
      return this
    }
  }

  private inner class ActionItemRenderer : JPanel(GridBagLayout()), ListCellRenderer<AccountMenuItem.Action> {
    val actionTextLabel = SimpleColoredComponent().apply {
      isOpaque = false
    }
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
      actionTextLabel.apply {
        clear()
        val foreground = UIUtil.getListForeground(selected, true)
        append(value.text, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foreground))
      }
      rightIconLabel.icon = value.rightIcon
      return this
    }
  }

  private inner class GroupItemRenderer : JPanel(GridBagLayout()), ListCellRenderer<AccountMenuItem.Group> {
    val actionTextLabel = SimpleColoredComponent()
    val rightIconLabel = JLabel()

    init {
      val topBottom = 3
      val insets = JBUI.insets(topBottom, leftInset, topBottom, 0)

      var gbc = GridBag().setDefaultAnchor(GridBag.WEST)
      gbc = gbc.nextLine().next().insets(insets)
      add(actionTextLabel, gbc)
      gbc = gbc.next().weightx(1.0)
      add(rightIconLabel, gbc)
      gbc = gbc.next().fillCellHorizontally().weightx(0.0).anchor(GridBag.EAST)
        .insets(JBUI.insets(topBottom, leftInset, topBottom, innerInset))
      add(JLabel(AllIcons.General.ArrowRight), gbc)
    }

    override fun getListCellRendererComponent(list: JList<out AccountMenuItem.Group>?,
                                              value: AccountMenuItem.Group,
                                              index: Int,
                                              selected: Boolean,
                                              focused: Boolean): JComponent {
      actionTextLabel.apply {
        clear()
        val foreground = UIUtil.getListForeground(selected, true)
        append(value.text, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foreground))
      }
      return this
    }
  }
}

fun browseAction(@Nls text: String, @NonNls link: String, showSeparatorAbove: Boolean = false): AccountMenuItem.Action {
  return AccountMenuItem.Action(text, { BrowserUtil.browse(link) }, AllIcons.Ide.External_link_arrow, showSeparatorAbove)
}
