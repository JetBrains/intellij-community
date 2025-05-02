// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.topHit

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.searcheverywhere.TopHitSEContributor
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.platform.searchEverywhere.SeActionItemPresentation
import com.intellij.platform.searchEverywhere.SeItemPresentation
import com.intellij.platform.searchEverywhere.SeOptionActionItemPresentation
import com.intellij.platform.searchEverywhere.SeSimpleItemPresentation
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil.toSize
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SeTopHitItemPresentationProvider {
  private val iconSize get() = JBUIScale.scale(16)

  suspend fun getPresentation(item: Any, project: Project): SeItemPresentation =
    readAction {
      val descriptionText = "TOP_HIT"

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
           val text = templatePresentation.getText()
           if (icon != null && icon.getIconWidth() <= iconSize && icon.getIconHeight() <= iconSize) {
             icon = toSize(icon, iconSize, iconSize)
           }

           SeSimpleItemPresentation((icon ?: EmptyIcon.ICON_16).rpcId(), text, descriptionText)
         }
        is OptionDescription -> {
          val text = TopHitSEContributor.getSettingText(item)
          //val attrs = SimpleTextAttributes.REGULAR_ATTRIBUTES
          //if (item is Changeable && (item as Changeable).hasChanged()) {
          //  if (selected) {
          //    attrs = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
          //  }
          //  else {
          //    val base = SimpleTextAttributes.LINK_BOLD_ATTRIBUTES
          //    attrs = base.derive(SimpleTextAttributes.STYLE_BOLD, base.getFgColor(), null, null)
          //  }
          //}
          if (item is BooleanOptionDescription) {
            SeOptionActionItemPresentation(SeActionItemPresentation.Common(text, _switcherState = item.isOptionEnabled),
                                           isBooleanOption = true)
          }
          else SeSimpleItemPresentation(EmptyIcon.ICON_16.rpcId(), text, descriptionText)
        }
        else -> {
          val presentation: ItemPresentation? = item as? ItemPresentation ?: (item as? NavigationItem)?.presentation

          SeSimpleItemPresentation((presentation?.getIcon(false) ?: EmptyIcon.ICON_16).rpcId(),
                                   presentation?.presentableText ?: item.toString(),
                                   descriptionText)
        }
      }
    }
}