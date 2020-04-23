// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.NonEmptyActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diff.impl.GenericDataProvider
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewResolvedThreadsToggleAction
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewThreadsReloadAction
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewThreadsToggleAction
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesDiffHelper
import org.jetbrains.plugins.github.util.GHToolbarLabelAction

class GHPRShowDiffAction : DumbAwareAction() {

  override fun update(e: AnActionEvent) {
    val project = e.project
    val selection = e.getData(VcsDataKeys.CHANGES)
    e.presentation.isEnabled = project != null && selection?.isNotEmpty() == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val selection = e.getRequiredData(VcsDataKeys.CHANGES)
    if (selection.isEmpty()) return

    val diffHelper = e.getData(GHPRChangesDiffHelper.DATA_KEY)
    val actionDataContext = e.getData(GHPRActionKeys.ACTION_DATA_CONTEXT)
    val producers = selection.mapNotNull {
      getDiffRequestProducer(project, it, diffHelper, actionDataContext)
    }
    if (producers.isEmpty()) return

    DiffManager.getInstance().showDiff(project, ChangeDiffRequestChain(producers, 0), DiffDialogHints.DEFAULT)
  }

  private fun getDiffRequestProducer(project: Project, change: Change,
                                     diffHelper: GHPRChangesDiffHelper?,
                                     actionDataContext: GHPRActionDataContext?): ChangeDiffRequestProducer? {

    val dataKeys: MutableMap<Key<out Any>, Any?> = mutableMapOf()

    val diffComputer = diffHelper?.getDiffComputer(change)
    if (diffComputer != null) {
      dataKeys[DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER] = diffComputer
    }

    val reviewSupport = diffHelper?.getReviewSupport(change)
    if (reviewSupport != null) {
      dataKeys[GHPRDiffReviewSupport.KEY] = reviewSupport
      dataKeys[DiffUserDataKeys.DATA_PROVIDER] = GenericDataProvider().apply {
        putData(GHPRActionKeys.ACTION_DATA_CONTEXT, actionDataContext)
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