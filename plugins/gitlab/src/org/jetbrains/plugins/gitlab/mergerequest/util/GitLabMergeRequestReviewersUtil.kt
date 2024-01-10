// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.util

import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.popup.ChooserPopupUtil
import com.intellij.collaboration.ui.util.popup.PopupItemPresentation
import com.intellij.collaboration.ui.util.popup.SelectablePopupItemPresentation
import com.intellij.collaboration.ui.util.popup.SimpleSelectablePopupItemRenderer
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO

internal object GitLabMergeRequestReviewersUtil {
  suspend fun selectReviewer(
    point: RelativePoint,
    originalReviewersIds: Set<String>,
    potentialReviewers: Flow<Result<List<GitLabUserDTO>>>,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): List<GitLabUserDTO>? {
    val selectedReviewer = ChooserPopupUtil.showAsyncChooserPopup(
      point,
      potentialReviewers,
      filteringMapper = { user -> user.username },
      renderer = SimpleSelectablePopupItemRenderer.create { reviewer ->
        SelectablePopupItemPresentation.Simple(
          reviewer.username,
          avatarIconsProvider.getIcon(reviewer, Avatar.Sizes.BASE),
          null,
          isSelected = originalReviewersIds.contains(reviewer.id)
        )
      }
    ) ?: return null

    return if (originalReviewersIds.contains(selectedReviewer.id)) emptyList() else listOf(selectedReviewer)
  }

  suspend fun selectReviewers(
    point: RelativePoint,
    originalReviewersIds: Set<String>,
    potentialReviewers: Flow<Result<List<GitLabUserDTO>>>,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): List<GitLabUserDTO> {
    return ChooserPopupUtil.showAsyncMultipleChooserPopup(
      point,
      potentialReviewers,
      presenter = { reviewer ->
        PopupItemPresentation.Simple(
          reviewer.username,
          avatarIconsProvider.getIcon(reviewer, Avatar.Sizes.BASE),
          null,
        )
      },
      isOriginallySelected = { selectedReviewer -> originalReviewersIds.contains(selectedReviewer.id) }
    )
  }
}