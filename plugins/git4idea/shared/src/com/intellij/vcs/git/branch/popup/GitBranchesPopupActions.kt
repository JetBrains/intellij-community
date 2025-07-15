// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch.popup

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.ui.popup.ActionPopupOptions
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.PopupFactoryImpl
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
object GitBranchesPopupActions {
  @Language("devkit-action-id")
  const val NEW_UI_TOP_LEVEL_ACTIONS_ACTION_GROUP: @NonNls String = "Git.Experimental.Branch.Popup.Actions"

  @Language("devkit-action-id")
  const val TOP_LEVEL_ACTIONS_ACTION_GROUP: @NonNls String = "Git.Branches.List"

  /**
   * Actions from this data group are wrapped in [GitBranchesPopupBase] to pass the text field in a data context
   * even when it isn't focused
   */
  @Language("devkit-action-id")
  const val SPEED_SEARCH_ACTION_GROUP: @NonNls String = "Git.Branches.Popup.SpeedSearch"

  val MAIN_POPUP_ACTION_PLACE: @NonNls String = ActionPlaces.getPopupPlace("GitBranchesPopup.TopLevel.Branch.Actions")

  val NESTED_POPUP_ACTION_PLACE: @NonNls String = ActionPlaces.getPopupPlace("GitBranchesPopup.SingleRepo.Branch.Actions")

  fun createTopLevelActionItems(
    dataContext: DataContext,
    actionGroupId: String,
    presentationFactory: PresentationFactory,
  ): List<PopupFactoryImpl.ActionItem> {
    val actionGroup = ActionManager.getInstance().getAction(actionGroupId) as? ActionGroup ?: return emptyList()

    val actionItems = ActionPopupStep.createActionItems(
      actionGroup, dataContext, MAIN_POPUP_ACTION_PLACE, presentationFactory,
      ActionPopupOptions.showDisabled())

    if (actionItems.singleOrNull()?.action == Utils.EMPTY_MENU_FILLER) {
      return emptyList()
    }

    return actionItems
  }
}