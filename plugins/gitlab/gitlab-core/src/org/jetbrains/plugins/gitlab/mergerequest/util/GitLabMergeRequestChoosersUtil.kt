// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.util

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil
import com.intellij.collaboration.ui.codereview.list.search.PopupConfig
import com.intellij.collaboration.ui.codereview.list.search.ShowDirection
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.popup.PopupItemPresentation
import com.intellij.collaboration.util.IncrementallyComputedValue
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.ColorIcon
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabLabel

internal object GitLabMergeRequestChoosersUtil {
  suspend fun chooseUser(
    point: RelativePoint,
    users: StateFlow<IncrementallyComputedValue<List<GitLabUserDTO>>>,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
    showDirection: ShowDirection = ShowDirection.BELOW,
  ): GitLabUserDTO? =
    ChooserPopupUtil.showChooserPopupWithIncrementalLoading(
      point,
      users,
      getUserPresenter(avatarIconsProvider),
      PopupConfig.DEFAULT.copy(showDirection = showDirection)
    )

  suspend fun chooseUsers(
    point: RelativePoint,
    choseUsers: List<GitLabUserDTO>,
    users: StateFlow<IncrementallyComputedValue<List<GitLabUserDTO>>>,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
    showDirection: ShowDirection = ShowDirection.BELOW,
  ): List<GitLabUserDTO> =
    ChooserPopupUtil.showMultipleChooserPopupWithIncrementalLoading(
      point,
      choseUsers,
      users,
      getUserPresenter(avatarIconsProvider),
      PopupConfig.DEFAULT.copy(showDirection = showDirection)
    )

  suspend fun chooseLabels(
    point: RelativePoint,
    chosenLabels: List<GitLabLabel>,
    potentialLabels: StateFlow<IncrementallyComputedValue<List<GitLabLabel>>>,
    showDirection: ShowDirection = ShowDirection.BELOW,
  ): List<GitLabLabel> = ChooserPopupUtil.showMultipleChooserPopupWithIncrementalLoading(
    point,
    chosenLabels,
    potentialLabels,
    presenter = getLabelPresenter(),
    PopupConfig.DEFAULT.copy(showDirection = showDirection)
  )

  fun getUserPresenter(avatarIconsProvider: IconsProvider<GitLabUserDTO>): (GitLabUserDTO) -> PopupItemPresentation {
    return {
      PopupItemPresentation.Simple(
        it.username,
        avatarIconsProvider.getIcon(it, Avatar.Sizes.BASE),
        it.name,
      )
    }
  }

  fun getLabelPresenter(): (GitLabLabel) -> PopupItemPresentation {
    return {
      val icon = runCatching {
        val color = CollaborationToolsUIUtil.getLabelBackground(it.colorHex)
        ColorIcon(16, color)
      }
      PopupItemPresentation.Simple(
        it.title,
        icon.getOrNull(),
      )
    }
  }
}