// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FeaturePromoBundle.message
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.searchEverywhere.SeActionItemPresentation
import com.intellij.platform.searchEverywhere.SeOptionActionItemPresentation
import com.intellij.platform.searchEverywhere.SeRunnableActionItemPresentation
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListItemRow
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListRow
import com.intellij.ui.ColorUtil
import com.intellij.ui.HtmlToSimpleColoredComponentConverter
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.render.IconCompOptionalCompPanel
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.*
import com.intellij.util.ui.StartupUiUtil.isUnderDarcula
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Color
import java.awt.Font
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.border.Border

@Internal
class SeActionItemPresentationRenderer(private val resultsList: JList<SeResultListRow>) {
  fun get(listFont: Font = StartupUiUtil.labelFont, patternProvider: () -> String): ListCellRenderer<SeResultListRow> = listCellRenderer {
    val presentation = (value as SeResultListItemRow).item.presentation as SeActionItemPresentation

    val showIcon = UISettings.getInstance().showIconsInMenus
    val commonData = presentation.commonData
    val pattern = patternProvider()
    val selected = selected
    val hasFocus = cellHasFocus
    selectionColor = UIUtil.getListBackground(selected, selected)

    var groupForeground: Color =
      if (selected) NamedColorUtil.getListSelectionForeground(true)
      else NamedColorUtil.getInactiveTextColor()

    val eastBorder: Border = JBUI.Borders.emptyRight(2)
    val switcherState = commonData.switcherState

    when (presentation) {
      is SeRunnableActionItemPresentation -> {
        val disabled = !selected && !presentation.isEnabled

        if (disabled) {
          groupForeground = UIUtil.getLabelDisabledForeground()
        }

        if (showIcon) {
          val icon =
            presentation.selectedIconId?.takeIf { selected }?.icon()
            ?: presentation.iconId?.icon()
            ?: EMPTY_ICON
          icon(GotoActionModel.createLayeredIcon(icon, disabled))
        }

        presentation.promo?.let { promo ->
          //TODO: customizePromoAction(anAction, bg, eastBorder, groupForeground, panel)
        }

        toolTipText = presentation.toolTip

        val actionId = presentation.actionId
        var name = getName(presentation.text, commonData.location, switcherState != null)
        name = cutName(name, presentation.shortcut, resultsList, listFont)

        text(name) {
          font = listFont
          foreground = GotoActionModel.defaultActionForeground(selected, hasFocus, presentation.isEnabled)

          // TODO: Should we handle HTML? (see: appendWithColoredMatches(nameComponent, presentation.text, pattern, fg, selected))
          speedSearchRange(name, pattern, selected)?.let {
            speedSearch {
              ranges = it
            }
          }
        }

        if (UISettings.getInstance().showInplaceCommentsInternal && actionId != null) {
          text(actionId) {
            attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
          }
        }

        presentation.shortcut?.let { shortcutText ->
          @Suppress("HardCodedStringLiteral")
          text(shortcutText) {
            attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER or SimpleTextAttributes.STYLE_BOLD, groupForeground)
          }
        }
      }

      is SeOptionActionItemPresentation -> {
        if (!selected && !presentation.isBooleanOption) {
          val descriptorBg = if (isUnderDarcula) {
            ColorUtil.brighter(UIUtil.getListBackground(), 1)
          }
          else {
            JBUI.CurrentTheme.BigPopup.LIST_SETTINGS_BACKGROUND
          }
          background = descriptorBg
        }

        if (showIcon) {
          icon(EMPTY_ICON)
        }

        text(presentation.text) {
          font = listFont
          foreground = UIUtil.getListForeground(selected, hasFocus)

          // TODO: Should we handle HTML? (see: appendWithColoredMatches(nameComponent, presentation.text, pattern, fg, selected))
          speedSearchRange(presentation.text, pattern, selected)?.let {
            speedSearch {
              ranges = it
            }
          }
        }
      }
    }


    if (switcherState != null) {
      switch(isOn = switcherState) {
        align = LcrInitParams.Align.RIGHT
      }
    }
    else presentation.commonData.location?.let { location ->
      val groupLabel = JLabel(location)
      groupLabel.border = eastBorder
      groupLabel.foreground = groupForeground

      text(location) {
        align = LcrInitParams.Align.RIGHT
      }
    }
  }

  private val EMPTY_ICON: Icon = EmptyIcon.ICON_16
  private val TOGGLE_BUTTON_BORDER: Border = JBUI.Borders.empty(0, 2)

  private fun customizePromoAction(
    promoAction: SeRunnableActionItemPresentation.Promo,
    panelBackground: Color,
    eastBorder: Border,
    groupFg: Color,
    panel: IconCompOptionalCompPanel<SimpleColoredComponent>,
  ) {
    val promo = SimpleColoredComponent()
    promo.background = panelBackground
    promo.foreground = groupFg
    promo.setIcon(AllIcons.Ide.External_link_arrow)
    promo.isIconOnTheRight = true
    promo.isTransparentIconBackground = true
    promo.append(promoAction.callToActionText)

    val upgradeTo = SimpleColoredComponent()
    val icon = promoAction.productIconId?.icon()
    upgradeTo.setIcon(icon)
    upgradeTo.background = panelBackground
    upgradeTo.foreground = groupFg
    upgradeTo.isIconOnTheRight = true
    upgradeTo.append(message("get.prefix") + " ")
    upgradeTo.isTransparentIconBackground = true

    val compositeUpgradeHint = JBUI.Panels.simplePanel(promo)
    if (icon != null) {
      compositeUpgradeHint.addToLeft(upgradeTo)
    }

    compositeUpgradeHint.andTransparent()

    compositeUpgradeHint.border = eastBorder

    panel.right = compositeUpgradeHint
  }

  private fun cutName(
    name: @NlsActions.ActionText String,
    shortcutText: @NlsSafe String?,
    list: JList<*>,
    font: Font,
  ): @NlsActions.ActionText String {
    var name = name
    if (!list.isShowing || list.width <= 0) {
      return StringUtil.first(name, 60, true) // fallback to previous behaviour
    }

    //we cannot cut HTML formatted strings
    if (name.startsWith("<html>")) return name

    // we have a min size for SE, which is ~40 symbols, don't spend time for trimming, let's use a shortcut
    if (name.length < 40) return name

    // TODO: Free space is calculated very incorrect
    val freeSpace = calcFreeSpace(list, font, shortcutText)

    if (freeSpace <= 0) {
      return name
    }

    val fm = resultsList.getFontMetrics(font)
    val strWidth = fm.stringWidth(name)
    if (strWidth <= freeSpace) {
      return name
    }

    var cutSymbolIndex = (((freeSpace.toDouble() - fm.stringWidth("...")) / strWidth) * name.length).toInt()
    cutSymbolIndex = Integer.max(1, cutSymbolIndex)
    name = name.substring(0, cutSymbolIndex)
    while (fm.stringWidth("$name...") > freeSpace && name.length > 1) {
      name = name.substring(0, name.length - 1)
    }

    return name.trim { it <= ' ' } + "..."
  }

  private fun calcFreeSpace(list: JList<*>, font: Font, shortcutText: String?): Int {
    var freeSpace = (list.width
                     - (list.insets.right + list.insets.left))
    //- panel.calculateNonResizeableWidth()
    //- (insets.right + insets.left)
    //- (ipad.right + ipad.left))

    if (StringUtil.isNotEmpty(shortcutText)) {
      val fm = list.getFontMetrics(font.deriveFont(Font.BOLD))
      freeSpace -= fm.stringWidth(" $shortcutText")
    }

    return freeSpace
  }

  private fun getName(text: @NlsActions.ActionText String?, groupName: @NlsActions.ActionText String?, toggle: Boolean): @NlsActions.ActionText String {
    if (text != null && text.startsWith("<html>") && text.endsWith("</html>")) {
      val rawText = text.substring(6, text.length - 7)
      return "<html>" + getName(rawText, groupName, toggle) + "</html>"
    }
    return if (toggle && StringUtil.isNotEmpty(groupName))
      (if (StringUtil.isNotEmpty(text))
        "$groupName: $text"
      else
        groupName)!!
    else
      (text ?: "")
  }

  private fun speedSearchRange(
    name: @NlsActions.ActionText String,
    pattern: @NlsSafe String,
    selected: Boolean,
  ): List<TextRange>? {
    if (!selected) return null

    val matchStart = StringUtil.indexOfIgnoreCase(name, pattern, 0)
    if (matchStart >= 0) {
      return listOf(TextRange.from(matchStart, pattern.length))
    }

    return null
  }

  private fun appendWithColoredMatches(
    nameComponent: SimpleColoredComponent,
    name: @NlsActions.ActionText String,
    pattern: @NlsSafe String,
    fg: Color,
    selected: Boolean,
  ) {
    var name = name
    val plain = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fg)

    if (name.startsWith("<html>")) {
      HtmlToSimpleColoredComponentConverter(HtmlToSimpleColoredComponentConverter.DEFAULT_TAG_HANDLER).appendHtml(nameComponent, name, plain)
      name = nameComponent.getCharSequence(false).toString()
    }
    else {
      nameComponent.append(name, plain)
    }

    nameComponent.setDynamicSearchMatchHighlighting(false)
    if (selected) {
      val matchStart = StringUtil.indexOfIgnoreCase(name, pattern, 0)
      if (matchStart >= 0) {
        nameComponent.setDynamicSearchMatchHighlighting(true)
        val fragments = listOf(TextRange.from(matchStart, pattern.length))
        SpeedSearchUtil.applySpeedSearchHighlighting(nameComponent, fragments, true)
      }
    }
  }
}