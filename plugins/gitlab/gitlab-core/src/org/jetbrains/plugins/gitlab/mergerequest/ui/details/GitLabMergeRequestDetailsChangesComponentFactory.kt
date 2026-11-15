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
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestActionPlaces
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangeListViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangesViewModel
import org.jetbrains.plugins.gitlab.mergerequest.util.localizedMessageOrClassName
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

@ApiStatus.Internal
object GitLabMergeRequestDetailsChangesComponentFactory {

  fun create(cs: CoroutineScope, vm: GitLabMergeRequestChangesViewModel): JComponent {
    val wrapper = Wrapper(LoadingLabel()).apply {
      bindContentIn(cs, vm.changeListVm) { res ->
        res.result?.let {
          it.fold(onSuccess = {
            createChangesTree(it)
          }, onFailure = {
            SimpleHtmlPane(it.localizedMessageOrClassName())
          })
        } ?: LoadingLabel()
      }
    }
    val scrollPane = TransparentScrollPane(wrapper).apply {
      horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED
      ScrollableContentBorder.setup(scrollPane = this, sides = Side.TOP_AND_BOTTOM, targetComponent = this)
    }
    // The toggle stays below the scroll pane, so a long change list does not scroll it out of view.
    val testSourcesToggle = Wrapper().apply {
      bindContentIn(cs, vm.changeListVm) { res ->
        res.result?.getOrNull()?.takeIf { it.testSources.count > 0 }?.let(::createTestSourcesToggle)
      }
    }
    return JPanel(BorderLayout()).apply {
      isOpaque = false
      add(scrollPane, BorderLayout.CENTER)
      add(testSourcesToggle, BorderLayout.SOUTH)
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

  private fun createTestSourcesToggle(vm: GitLabMergeRequestChangeListViewModel): JComponent {
    val (count, hidden) = vm.testSources
    val label = JBLabel(GitLabBundle.message(if (hidden) "merge.request.details.changes.test.sources.hidden"
                                             else "merge.request.details.changes.test.sources", count)).apply {
      componentStyle = UIUtil.ComponentStyle.SMALL
      foreground = UIUtil.getContextHelpForeground()
    }
    val link = ActionLink(GitLabBundle.message(if (hidden) "merge.request.details.changes.test.sources.show"
                                               else "merge.request.details.changes.test.sources.hide")) {
      vm.setTestSourcesHidden(!hidden)
    }.apply {
      font = JBUI.Fonts.smallFont()
    }
    return JPanel(HorizontalLayout(JBUI.scale(6))).apply {
      isOpaque = false
      border = JBUI.Borders.empty(4, 8)
      add(label)
      add(link)
    }
  }
}