// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actions.NonEmptyActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.diff.impl.GenericDataProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRFixedActionDataContext
import org.jetbrains.plugins.github.pullrequest.action.GHPRReviewSubmitAction
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewResolvedThreadsToggleAction
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewThreadsReloadAction
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewThreadsToggleAction
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.util.GHToolbarLabelAction
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.border.Border
import javax.swing.tree.DefaultTreeModel

internal class GHPRChangesBrowser(private val model: GHPRChangesModel,
                                  private val diffHelper: GHPRChangesDiffHelper,
                                  private val project: Project)
  : ChangesBrowserBase(project, false, false),
    ComponentWithEmptyText {

  init {
    init()
    model.addStateChangesListener {
      myViewer.rebuildTree()
    }
  }

  override fun canShowDiff(): Boolean {
    val selection = VcsTreeModelData.getListSelectionOrAll(myViewer)
    return selection.list.any { it is Change && ChangeDiffRequestProducer.canCreate(project, it) }
  }

  override fun getDiffRequestProducer(userObject: Any): ChangeDiffRequestChain.Producer? {
    return if (userObject is Change) {
      val dataKeys: MutableMap<Key<out Any>, Any?> = mutableMapOf()

      val diffComputer = diffHelper.getDiffComputer(userObject)
      if (diffComputer != null) {
        dataKeys[DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER] = diffComputer
      }

      val reviewSupport = diffHelper.getReviewSupport(userObject)
      if (reviewSupport != null) {
        dataKeys[GHPRDiffReviewSupport.KEY] = reviewSupport
        val currentDataContext =
          GHPRActionKeys.ACTION_DATA_CONTEXT.getData(DataManager.getInstance().getDataContext(this))
        val dataContext = currentDataContext?.let { ctx ->
          ctx.pullRequestDataProvider?.let {
            GHPRFixedActionDataContext(ctx, it)
          }
        }

        dataKeys[DiffUserDataKeys.DATA_PROVIDER] = GenericDataProvider().apply {
          putData(GHPRActionKeys.ACTION_DATA_CONTEXT, dataContext)
          putData(GHPRDiffReviewSupport.DATA_KEY, reviewSupport)
        }
        val viewOptionsGroup = NonEmptyActionGroup().apply {
          isPopup = true
          templatePresentation.text = "View Options"
          templatePresentation.icon = AllIcons.Actions.Show
          add(GHPRDiffReviewThreadsToggleAction())
          add(GHPRDiffReviewResolvedThreadsToggleAction())
        }

        dataKeys[DiffUserDataKeys.CONTEXT_ACTIONS] = listOf(GHToolbarLabelAction("Review:"),
                                                            viewOptionsGroup,
                                                            GHPRDiffReviewThreadsReloadAction(),
                                                            GHPRReviewSubmitAction())
      }
      ChangeDiffRequestProducer.create(myProject, userObject, dataKeys)
    }
    else null
  }

  override fun createToolbarActions(): List<AnAction> {
    return super.createToolbarActions() + GHPRReviewSubmitAction()
  }

  override fun getEmptyText() = myViewer.emptyText

  override fun createTreeList(project: Project, showCheckboxes: Boolean, highlightProblems: Boolean): ChangesBrowserTreeList {
    return super.createTreeList(project, showCheckboxes, highlightProblems).also {
      it.addFocusListener(object : FocusListener {
        override fun focusGained(e: FocusEvent?) {
          if (it.isSelectionEmpty && !it.isEmpty) TreeUtil.selectFirstNode(it)
        }

        override fun focusLost(e: FocusEvent?) {}
      })
    }
  }

  override fun createViewerBorder(): Border = IdeBorderFactory.createBorder(SideBorder.TOP)

  override fun buildTreeModel(): DefaultTreeModel {
    return model.buildChangesTree(grouping)
  }

  class ToggleZipCommitsAction : ToggleAction("Commit"), DumbAware {

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabledAndVisible = e.getData(DATA_KEY) is GHPRChangesBrowser
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      val project = e.project ?: return false
      return !GithubPullRequestsProjectUISettings.getInstance(project).zipChanges
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val project = e.project ?: return
      GithubPullRequestsProjectUISettings.getInstance(project).zipChanges = !state
    }
  }
}