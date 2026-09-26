// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.topHit

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.searcheverywhere.TopHitSEContributor
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.presentations.SeActionItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeBasicItemPresentationBuilder
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeOptionActionItemPresentation
import com.intellij.ui.Changeable
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil.toSize
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SeTopHitItemPresentationProvider {
  private val iconSize get() = JBUIScale.scale(16)

  suspend fun getPresentation(item: Any, project: Project, extendedInfo: SeExtendedInfo?, isMultiSelectionSupported: Boolean): SeItemPresentation =
    readAction {
      when (item) {
        is AnAction -> {
          val templatePresentation: Presentation = item.getTemplatePresentation()
          var icon = templatePresentation.getIcon()
          if (item is ActivateToolWindowAction) {
            val id = item.toolWindowId
            val toolWindow = getInstance(project).getToolWindow(id)
            if (toolWindow != null) {
              icon = toolWindow.getIcon()
            }
          }
          val text = templatePresentation.text
          if (icon != null && icon.iconWidth <= iconSize && icon.iconHeight <= iconSize) {
            icon = toSize(icon, iconSize, iconSize)
          }

          SeBasicItemPresentationBuilder()
            .withIcon(icon ?: EmptyIcon.ICON_16)
            .withText(text)
            .withExtendedInfo(extendedInfo)
            .withMultiSelectionSupported(isMultiSelectionSupported)
            .build()
         }
        is OptionDescription -> {
          val text = TopHitSEContributor.getSettingText(item)
          val isChangedChangeable = item is Changeable && (item as Changeable).hasChanged()

          val attributes = if (isChangedChangeable) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
          val base = SimpleTextAttributes.LINK_BOLD_ATTRIBUTES
          val selectedAttributes =
            if (isChangedChangeable) base.derive(SimpleTextAttributes.STYLE_BOLD, base.fgColor, null, null)
            else null

          if (item is BooleanOptionDescription) {
              SeOptionActionItemPresentation(
                  SeActionItemPresentation.Common(text, _switcherState = item.isOptionEnabled),
                  isBooleanOption = true, isMultiSelectionSupported = isMultiSelectionSupported
              )
          }
          else SeBasicItemPresentationBuilder()
            .withIcon(EmptyIcon.ICON_16)
            .withText(text)
            .withTextAttributes(attributes)
            .withSelectedTextAttributes(selectedAttributes)
            .withExtendedInfo(extendedInfo)
            .withMultiSelectionSupported(isMultiSelectionSupported)
            .build()
        }
        else -> {
          val presentation: ItemPresentation? = item as? ItemPresentation ?: (item as? NavigationItem)?.presentation

          SeBasicItemPresentationBuilder()
            .withIcon(presentation?.getIcon(false) ?: EmptyIcon.ICON_16)
            .withText(presentation?.presentableText ?: item.toString())
            .withExtendedInfo(extendedInfo)
            .withMultiSelectionSupported(isMultiSelectionSupported)
            .build()
        }
      }
    }
}