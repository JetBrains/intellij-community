// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.NonEmptyActionGroup
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diff.impl.GenericDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.history.VcsDiffUtil
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRReviewSubmitAction
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewResolvedThreadsToggleAction
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewThreadsReloadAction
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewThreadsToggleAction
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.util.GHToolbarLabelAction

interface GHPRChangesDiffHelper {
  fun setUp(changesProvider: GHPRChangesProvider)
  fun reset()

  fun getReviewSupport(change: Change): GHPRDiffReviewSupport?
  fun getDiffComputer(change: Change): DiffUserDataKeysEx.DiffComputer?

  companion object {
    val DATA_KEY = DataKey.create<GHPRChangesDiffHelper>("Github.PullRequest.Diff.Helper")

    fun getDiffRequestProducer(project: Project, change: Change, dataProvider: GHPRDataProvider?, diffHelper: GHPRChangesDiffHelper?)
      : ChangeDiffRequestProducer? {

      val dataKeys: MutableMap<Key<out Any>, Any?> = mutableMapOf()

      VcsDiffUtil.putFilePathsIntoChangeContext(change, dataKeys)

      val diffComputer = diffHelper?.getDiffComputer(change)
      if (diffComputer != null) {
        dataKeys[DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER] = diffComputer
      }

      val reviewSupport = diffHelper?.getReviewSupport(change)
      if (reviewSupport != null) {
        dataKeys[GHPRDiffReviewSupport.KEY] = reviewSupport
        dataKeys[DiffUserDataKeys.DATA_PROVIDER] = GenericDataProvider().apply {
          putData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER, dataProvider)
          putData(GHPRDiffReviewSupport.DATA_KEY, reviewSupport)
        }
        val viewOptionsGroup = NonEmptyActionGroup().apply {
          isPopup = true
          templatePresentation.text = GithubBundle.message("pull.request.diff.view.options")
          templatePresentation.icon = AllIcons.Actions.Show
          add(GHPRDiffReviewThreadsToggleAction())
          add(GHPRDiffReviewResolvedThreadsToggleAction())
        }

        dataKeys[DiffUserDataKeys.CONTEXT_ACTIONS] = listOf(GHToolbarLabelAction(GithubBundle.message("pull.request.diff.review.label")),
                                                            viewOptionsGroup,
                                                            GHPRDiffReviewThreadsReloadAction(),
                                                            GHPRReviewSubmitAction())
      }
      return ChangeDiffRequestProducer.create(project, change, dataKeys)
    }
  }
}
