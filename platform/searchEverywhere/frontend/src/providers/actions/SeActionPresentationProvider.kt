// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.actions.ApplyIntentionAction
import com.intellij.ide.actions.searcheverywhere.PromoAction
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.ui.icons.rpcIdOrNull
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.getGroupName
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.ActionPresentationDecorator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.searchEverywhere.SeActionItemPresentation
import com.intellij.platform.searchEverywhere.SeItemPresentation
import com.intellij.platform.searchEverywhere.SeOptionActionItemPresentation
import com.intellij.platform.searchEverywhere.SeRunnableActionItemPresentation
import com.intellij.platform.searchEverywhere.SeRunnableActionItemPresentation.Promo
import com.intellij.util.text.nullize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SeActionPresentationProvider {
  suspend fun get(matchedValue: GotoActionModel.MatchedValue, extendedDescription: String?): SeItemPresentation {
    val value = matchedValue.value
    if (value is GotoActionModel.ActionWrapper) {
      var presentation = SeRunnableActionItemPresentation(commonData = SeActionItemPresentation.Common(text = "", extendedDescription = extendedDescription))

      val anAction = value.action
      val actionPresentation = value.presentation

      val toggle = anAction is ToggleAction
      if (toggle) {
        presentation = presentation.run {
          copy(commonData = commonData.copy(_switcherState = Toggleable.isSelected(actionPresentation)))
        }
      }

      val groupName = if (anAction is ApplyIntentionAction) null else value.getGroupName()
      if (groupName != null) {
        presentation = presentation.run {
          copy(commonData = commonData.copy(location = groupName))
        }
      }

      if (UISettings.getInstance().showIconsInMenus) {
        presentation = presentation.run {
          copy(iconId = actionPresentation.icon?.rpcIdOrNull(),
               selectedIconId = actionPresentation.selectedIcon?.rpcIdOrNull())
        }
      }

      if (anAction is PromoAction) {
        presentation = presentation.run {
          copy(promo = Promo(productIconId = anAction.promotedProductIcon?.rpcId(),
                             callToActionText = anAction.callToAction))
        }
      }

      presentation = presentation.run {
        copy(toolTip = actionPresentation.description, isEnabled = actionPresentation.isEnabled)
      }

      @NlsSafe val actionId = ActionManager.getInstance().getId(anAction)
      val shortcuts = KeymapUtil.getActiveKeymapShortcuts(actionId).getShortcuts()
      KeymapUtil.getPreferredShortcutText(shortcuts).nullize(true)?.takeIf { it.isNotEmpty() }?.let {
        presentation = presentation.run { copy(shortcut = it) }
      }

      val decoratedText = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        ActionPresentationDecorator.decorateTextIfNeeded(anAction, actionPresentation.text)
      }

      presentation = presentation.run {
        copy(commonData = commonData.copy(text = decoratedText))
      }

      return presentation
    }
    else if (value is OptionDescription) {
      val hit = GotoActionModel.GotoActionListCellRenderer.calcHit(value)
      var presentation = SeOptionActionItemPresentation(commonData = SeActionItemPresentation.Common(text = hit, extendedDescription = extendedDescription),)

      (value as? BooleanOptionDescription)?.isOptionEnabled.let {
        presentation = presentation.run {
          copy(commonData = commonData.copy(_switcherState = it))
        }
      }

      presentation = presentation.run {
        copy(commonData = commonData.copy(location = getGroupName(value)))
      }

      return presentation
    }

    return SeRunnableActionItemPresentation(SeActionItemPresentation.Common(text = "Unknown item"))
  }
}