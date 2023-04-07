// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.TransparentScrollPane
import com.intellij.collaboration.ui.codereview.changes.CodeReviewChangesTreeFactory
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.ScrollableContentBorder
import com.intellij.ui.Side
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangesViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

internal class GitLabMergeRequestDetailsChangesComponentFactory(private val project: Project) {

  fun create(cs: CoroutineScope, vm: GitLabMergeRequestChangesViewModel): JComponent {
    val wrapper = Wrapper(LoadingLabel())
    wrapper.bindContentIn(cs, vm.changesResult) { _, result ->
      result.fold(
        onSuccess = { createChangesTree(vm, it) },
        onFailure = { SimpleHtmlPane(it.localizedMessage) }
      )
    }
    return TransparentScrollPane(wrapper).apply {
      horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED
      ScrollableContentBorder.setup(scrollPane = this, sides = Side.TOP_AND_BOTTOM, targetComponent = wrapper)
    }
  }

  // TODO: do not recreate the tree
  private fun createChangesTree(vm: GitLabMergeRequestChangesViewModel, changes: Collection<Change>): JComponent {
    val changesModel = SingleValueModel(changes)
    return CodeReviewChangesTreeFactory(project, changesModel)
      .create(GitLabBundle.message("merge.request.details.changes.empty")).apply {
        doubleClickHandler = Processor { e ->
          if (EditSourceOnDoubleClickHandler.isToggleEvent(this, e)) return@Processor false
          vm.showDiff()
          true
        }
        enterKeyHandler = Processor {
          vm.showDiff()
          true
        }
      }.also {
        it.addSelectionListener {
          vm.updateSelectedChanges(VcsTreeModelData.getListSelectionOrAll(it).map { it as? Change })
        }
        vm.updateSelectedChanges(VcsTreeModelData.getListSelectionOrAll(it).map { it as? Change })

        it.installPopupHandler(ActionManager.getInstance().getAction("GitLab.Merge.Request.Changes.Popup") as ActionGroup)
      }
  }
}