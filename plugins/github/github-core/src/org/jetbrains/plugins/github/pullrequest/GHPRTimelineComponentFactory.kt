// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.LoadingTextLabel
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.util.fold
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.editor.colors.EditorColorsManager.getInstance
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.SingleComponentCenteringLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRFileEditorComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineViewModel
import org.jetbrains.plugins.github.ui.component.GHHtmlErrorPanel
import java.awt.BorderLayout
import java.awt.LayoutManager
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
object GHPRTimelineComponentFactory {
  @RequiresEdt
  fun create(
    project: Project, cs: CoroutineScope,
    projectVm: GHPRConnectedProjectViewModel, timelineVm: GHPRTimelineViewModel,
    pullRequest: GHPRIdentifier,
  ): JComponent {
    val panel = JBPanel<JBPanel<*>>().apply {
      background = JBColor.lazy { getInstance().globalScheme.defaultBackground }
    }
    cs.launchNow {
      timelineVm.detailsVm.details.collect {
        it.fold(
          onInProgress = {
            panel.setLayoutAndComponent(SingleComponentCenteringLayout(), LoadingTextLabel())
          },
          onSuccess = { details ->
            val timeline = GHPRFileEditorComponentFactory(cs, project, projectVm, timelineVm, details).create()
            panel.setLayoutAndComponent(BorderLayout(), timeline)
            //further updates will be handled by the timeline itself
            cancel()
          },
          onFailure = { error ->
            val errorStatusPresenter = ErrorStatusPresenter.simple(message("cannot.load.details"),
                                                                   descriptionProvider = GHHtmlErrorPanel::getLoadingErrorText)
            val errorPanel = ErrorStatusPanelFactory.create(error, errorStatusPresenter)
            panel.setLayoutAndComponent(SingleComponentCenteringLayout(), errorPanel)
          }
        )
      }
    }
    return UiDataProvider.wrapComponent(panel) { sink ->
      sink[GHPRActionKeys.PULL_REQUEST_ID] = pullRequest
      sink[GHPRActionKeys.PULL_REQUEST_URL] = timelineVm.detailsVm.details.value.getOrNull()?.url
    }
  }

  private fun JPanel.setLayoutAndComponent(newLayout: LayoutManager, component: JComponent) {
    removeAll()
    layout = newLayout
    add(component)
    revalidate()
    repaint()
  }
}