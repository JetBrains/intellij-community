// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.TransparentScrollPane
import com.intellij.collaboration.ui.codereview.changes.CodeReviewChangesTreeFactory
import com.intellij.collaboration.ui.codereview.setupCodeReviewProgressModel
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.ScrollableContentBorder
import com.intellij.ui.Side
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.plugins.gitlab.mergerequest.diff.ChangesSelection
import org.jetbrains.plugins.gitlab.mergerequest.diff.isEqual
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
          createChangesTree(it)
        }, onFailure = {
          SimpleHtmlPane(it.localizedMessage)
        })
      }
    }
    return TransparentScrollPane(wrapper).apply {
      horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED
      ScrollableContentBorder.setup(scrollPane = this, sides = Side.TOP_AND_BOTTOM, targetComponent = wrapper)
    }
  }

  private fun CoroutineScope.createChangesTree(vm: GitLabMergeRequestChangeListViewModel): JComponent {
    val changesModel = SingleValueModel<List<Change>>(emptyList())
    val tree = CodeReviewChangesTreeFactory(project, changesModel)
      .create(GitLabBundle.message("merge.request.details.changes.empty"), false).also { tree ->
        tree.doubleClickHandler = Processor { e ->
          if (EditSourceOnDoubleClickHandler.isToggleEvent(tree, e)) return@Processor false
          updateUserChangesSelection(vm, changesModel.value, tree)
          vm.showDiff()
          true
        }

        tree.enterKeyHandler = Processor {
          updateUserChangesSelection(vm, changesModel.value, tree)
          vm.showDiff()
          true
        }

        tree.installPopupHandler(ActionManager.getInstance().getAction("GitLab.Merge.Request.Changes.Popup") as ActionGroup)
      }.apply {
        val progressModel = GitLabMergeRequestProgressTreeModel(this@createChangesTree, vm)
        setupCodeReviewProgressModel(progressModel)
      }

    launchNow {
      // magic with selection to skip selection reset after model update
      val selectionListener = TreeSelectionListener {
        updateUserChangesSelection(vm, changesModel.value, tree)
      }

      vm.updates.collectLatest {
        tree.removeTreeSelectionListener(selectionListener)
        if (!it.changes.isEqual(changesModel.value)) {
          changesModel.value = it.changes
        }
        when (it) {
          is GitLabMergeRequestChangeListViewModel.Update.WithSelectAll -> {
            tree.invokeAfterRefresh {
              TreeUtil.selectFirstNode(tree)
              tree.addTreeSelectionListener(selectionListener)
            }
          }
          is GitLabMergeRequestChangeListViewModel.Update.WithSelectChange -> {
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

  private fun updateUserChangesSelection(vm: GitLabMergeRequestChangeListViewModel, allChanges: List<Change>, tree: AsyncChangesTree) {
    var fuzzy = false
    val changes = mutableListOf<Change>()
    VcsTreeModelData.selected(tree).iterateRawNodes().forEach {
      if (it.isLeaf) {
        val change = it.userObject as? Change
        changes.add(change!!)
      }
      else {
        fuzzy = true
      }
    }
    val selection = if (changes.isEmpty()) null
    else if (fuzzy) {
      ChangesSelection.Fuzzy(changes)
    }
    else {
      ChangesSelection.Precise(allChanges, changes[0])
    }
    vm.updateSelectedChanges(selection)
  }
}