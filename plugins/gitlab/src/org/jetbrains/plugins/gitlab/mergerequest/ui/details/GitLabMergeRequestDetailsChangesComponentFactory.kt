// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.TransparentScrollPane
import com.intellij.collaboration.ui.codereview.CodeReviewProgressTreeModelFromDetails
import com.intellij.collaboration.ui.codereview.changes.CodeReviewChangeListComponentFactory
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollableContentBorder
import com.intellij.ui.Side
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestActionPlaces
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangeListViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangesViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

internal object GitLabMergeRequestDetailsChangesComponentFactory {

  fun create(cs: CoroutineScope, vm: GitLabMergeRequestChangesViewModel): JComponent {
    val wrapper = Wrapper(LoadingLabel()).apply {
      bindContentIn(cs, vm.changeListVm) { res ->
        res.result?.let {
          it.fold(onSuccess = {
            createChangesTree(it)
          }, onFailure = {
            SimpleHtmlPane(it.localizedMessage ?: it.message ?: "${it.javaClass.name} occurred")
          })
        } ?: LoadingLabel()
      }
    }
    return TransparentScrollPane(wrapper).apply {
      horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED
      ScrollableContentBorder.setup(scrollPane = this, sides = Side.TOP_AND_BOTTOM, targetComponent = this)
    }
  }

  private fun CoroutineScope.createChangesTree(vm: GitLabMergeRequestChangeListViewModel): JComponent {
    val progressModel = CodeReviewProgressTreeModelFromDetails(this, vm)
    return CodeReviewChangeListComponentFactory.createIn(this, vm, progressModel,
                                                         GitLabBundle.message("merge.request.details.changes.empty")).also {
      val popupGroup = ActionManager.getInstance().getAction("GitLab.Merge.Request.Changes.Popup") as ActionGroup
      PopupHandler.installPopupMenu(it, popupGroup, GitLabMergeRequestActionPlaces.CHANGES_TREE_POPUP)
    }
  }
}