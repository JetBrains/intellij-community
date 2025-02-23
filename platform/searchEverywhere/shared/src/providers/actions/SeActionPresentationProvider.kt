// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.actions

import com.intellij.ide.actions.ApplyIntentionAction
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.getGroupName
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.searchEverywhere.SeActionItemPresentation
import com.intellij.platform.searchEverywhere.SeItemPresentation
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SeActionPresentationProvider: (GotoActionModel.MatchedValue) -> SeItemPresentation {
  override fun invoke(matchedValue: GotoActionModel.MatchedValue): SeItemPresentation {
    val value = matchedValue.value
    if (value is GotoActionModel.ActionWrapper) {
      var presentation = SeActionItemPresentation(text = "")

      val anAction = value.action
      val actionPresentation = value.presentation

      val toggle = anAction is ToggleAction
      if (toggle) {
        presentation = presentation.run { copy(switcherState = Toggleable.isSelected(actionPresentation)) }
      }

      val groupName = if (anAction is ApplyIntentionAction) null else value.getGroupName()
      if (groupName != null) {
        presentation = presentation.run { copy(location = groupName) }
      }

      //if (UISettings.getInstance().showIconsInMenus) {
      //  presentation = presentation.run { copy(icon = actionPresentation.icon) }
      //  //if (isSelected && presentation.getSelectedIcon() != null) {
      //  //  icon = presentation.getSelectedIcon();
      //  //}
      //}

      //if (anAction instanceof PromoAction promoAction) {
      //  customizePromoAction(promoAction, bg, eastBorder, groupFg, panel);
      //}

      @NlsSafe val actionId = ActionManager.getInstance().getId(anAction)
      val shortcuts = KeymapUtil.getActiveKeymapShortcuts(actionId).getShortcuts()
      val shortcutText = KeymapUtil.getPreferredShortcutText(shortcuts).nullize(true)?.let {
        presentation = presentation.run { copy(shortcut = it) }
      }

      //val text = ActionPresentationDecorator.decorateTextIfNeeded(anAction, actionPresentation.text)
      val text = actionPresentation.text
      presentation = presentation.run { copy(text = text) }

      return presentation
    }
    else if (value is OptionDescription) {
      val hit = GotoActionModel.GotoActionListCellRenderer.calcHit(value)
      var presentation = SeActionItemPresentation(text = hit)

      (value as? BooleanOptionDescription)?.isOptionEnabled.let {
        presentation = presentation.run { copy(switcherState = it) }
      }

      presentation = presentation.run { copy(location = getGroupName(value)) }
      return presentation
    }

    return SeActionItemPresentation(text = "Unknown item")
  }
}