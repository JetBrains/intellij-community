// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.TransparentScrollPane
import com.intellij.collaboration.ui.codereview.changes.CodeReviewChangesTreeFactory
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.details.model.updateSelectedChangesFromTree
import com.intellij.collaboration.ui.codereview.setupCodeReviewProgressModel
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.ui.ScrollableContentBorder
import com.intellij.ui.Side
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import com.intellij.collaboration.util.isEqual
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangeListViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangesViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.event.TreeSelectionListener

internal class GitLabMergeRequestDetailsChangesComponentFactory(private val project: Project) {

  fun create(cs: CoroutineScope, vm: GitLabMergeRequestChangesViewModel): JComponent {
    val wrapper = Wrapper(LoadingLabel()).apply {
      bindContentIn(cs, vm.changeListVm) { res ->
        res.fold(onSuccess = {
          createChangesTree(vm, it)
        }, onFailure = {
          SimpleHtmlPane(it.localizedMessage)
        })
      }
    }
    return TransparentScrollPane(wrapper).apply {
      horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED
      ScrollableContentBorder.setup(scrollPane = this, sides = Side.TOP_AND_BOTTOM, targetComponent = this)
    }
  }

  private fun CoroutineScope.createChangesTree(changesVm: GitLabMergeRequestChangesViewModel,
                                               vm: GitLabMergeRequestChangeListViewModel): JComponent {
    val changesModel = SingleValueModel<List<Change>>(emptyList())
    val tree = CodeReviewChangesTreeFactory(project, changesModel)
      .create(GitLabBundle.message("merge.request.details.changes.empty"), false).also { tree ->
        tree.doubleClickHandler = Processor { e ->
          if (EditSourceOnDoubleClickHandler.isToggleEvent(tree, e)) return@Processor false
          vm.updateSelectedChangesFromTree(changesModel.value, tree)
          vm.showDiff()
          true
        }

        tree.enterKeyHandler = Processor {
          vm.updateSelectedChangesFromTree(changesModel.value, tree)
          vm.showDiff()
          true
        }

        tree.installPopupHandler(ActionManager.getInstance().getAction("GitLab.Merge.Request.Changes.Popup") as ActionGroup)
      }.apply {
        val progressModel = GitLabMergeRequestProgressTreeModel(this@createChangesTree, changesVm)
        setupCodeReviewProgressModel(progressModel)
      }

    launchNow {
      // magic with selection to skip selection reset after model update
      val selectionListener = TreeSelectionListener {
        vm.updateSelectedChangesFromTree(changesModel.value, tree)
      }

      vm.updates.collectLatest {
        tree.removeTreeSelectionListener(selectionListener)
        if (!it.changes.isEqual(changesModel.value)) {
          changesModel.value = it.changes
        }
        when (it) {
          is CodeReviewChangeListViewModel.Update.WithSelectAll -> {
            tree.invokeAfterRefresh {
              TreeUtil.selectFirstNode(tree)
              tree.addTreeSelectionListener(selectionListener)
            }
          }
          is CodeReviewChangeListViewModel.Update.WithSelectChange -> {
            tree.invokeAfterRefresh {
              tree.setSelectedChanges(listOf(it.change))
              tree.addTreeSelectionListener(selectionListener)
            }
          }
        }
      }
    }

    return tree
  }
}