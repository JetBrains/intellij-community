// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.topHit

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
import com.intellij.platform.searchEverywhere.*
import com.intellij.ui.Changeable
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil.toSize
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SeTopHitItemPresentationProvider {
  private val iconSize get() = JBUIScale.scale(16)

  suspend fun getPresentation(item: Any, project: Project, extendedDescription: String?): SeItemPresentation =
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

           SeSimpleItemPresentation(iconId = (icon ?: EmptyIcon.ICON_16).rpcId(),
                                    text = text,
                                    extendedDescription = extendedDescription)
         }
        is OptionDescription -> {
          val text = TopHitSEContributor.getSettingText(item)
          val isChangedChangeable = item is Changeable && (item as Changeable).hasChanged()

          val textChunk = (if (isChangedChangeable) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES).let {
            SerializableTextChunk(text, it)
          }
          val base = SimpleTextAttributes.LINK_BOLD_ATTRIBUTES
          val selectedTextChunk =
            (if (isChangedChangeable)
              base.derive(SimpleTextAttributes.STYLE_BOLD, base.fgColor, null, null)
            else null)?.let {
              SerializableTextChunk(text, it)
            }

          if (item is BooleanOptionDescription) {
            SeOptionActionItemPresentation(SeActionItemPresentation.Common(text + " TOP_HIT", _switcherState = item.isOptionEnabled),
                                           isBooleanOption = true)
          }
          else SeSimpleItemPresentation(iconId = EmptyIcon.ICON_16.rpcId(),
                                        textChunk = textChunk,
                                        selectedTextChunk = selectedTextChunk,
                                        extendedDescription = extendedDescription)
        }
        else -> {
          val presentation: ItemPresentation? = item as? ItemPresentation ?: (item as? NavigationItem)?.presentation

          SeSimpleItemPresentation(iconId = (presentation?.getIcon(false) ?: EmptyIcon.ICON_16).rpcId(),
                                   text = presentation?.presentableText ?: item.toString(),
                                   extendedDescription = extendedDescription)
        }
      }
    }
}