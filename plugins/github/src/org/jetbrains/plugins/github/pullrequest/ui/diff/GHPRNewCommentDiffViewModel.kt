// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff

import com.intellij.collaboration.async.classAsCoroutineName
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentPosition
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModelImpl

interface GHPRNewCommentDiffViewModel {
  val position: GHPRReviewCommentPosition
  val newCommentVm: GHPRReviewNewCommentEditorViewModel

  fun requestFocus()
}

internal class GHPRNewCommentDiffViewModelImpl(project: Project,
                                               parentCs: CoroutineScope,
                                               dataContext: GHPRDataContext,
                                               dataProvider: GHPRDataProvider,
                                               override val position: GHPRReviewCommentPosition,
                                               onCancel: () -> Unit)
  : GHPRNewCommentDiffViewModel {
  private val cs = parentCs.childScope(classAsCoroutineName())

  override val newCommentVm = GHPRReviewNewCommentEditorViewModelImpl(project, cs, dataProvider.reviewData,
                                                                      dataContext.repositoryDataService.remoteCoordinates.repository,
                                                                      dataContext.securityService.currentUser,
                                                                      dataContext.avatarIconsProvider,
                                                                      position, onCancel)

  override fun requestFocus() {
    newCommentVm.requestFocus()
  }

  fun destroy() {
    cs.cancel()
  }
}