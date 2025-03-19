// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.LoadingTextLabel
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.util.getOrNull
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsManager.TOPIC
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.SingleComponentCenteringLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.i18n.GithubBundle
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
  fun create(
    project: Project, cs: CoroutineScope,
    projectVm: GHPRConnectedProjectViewModel, timelineVm: GHPRTimelineViewModel,
    pullRequest: GHPRIdentifier,
  ): JComponent {
    return doCreateContent(project, cs, projectVm, timelineVm).apply {
      isOpaque = true
      background = EditorColorsManager.getInstance().globalScheme.defaultBackground
    }.also {
      ApplicationManager.getApplication().messageBus.connect(cs)
        .subscribe(TOPIC, EditorColorsListener { scheme -> it.background = scheme?.defaultBackground })

      DataManager.registerDataProvider(it) { dataId ->
        when {
          GHPRActionKeys.PULL_REQUEST_ID.`is`(dataId) -> pullRequest
          GHPRActionKeys.PULL_REQUEST_URL.`is`(dataId) -> timelineVm.detailsVm.details.value.getOrNull()?.url
          else -> null
        }
      }
    }
  }

  private fun doCreateContent(
    project: Project, cs: CoroutineScope,
    projectVm: GHPRConnectedProjectViewModel, timelineVm: GHPRTimelineViewModel,
  ): JComponent {
    val panel = JBPanel<JBPanel<*>>()
    cs.launchNow {
      timelineVm.detailsVm.details.collectLatest {
        when (val result = it.result) {
          null -> panel.setLayoutAndComponent(SingleComponentCenteringLayout(), LoadingTextLabel())
          else -> result
            .fold({ details ->
                    //further updates will be handled by the timeline itself
                    val timeline = GHPRFileEditorComponentFactory(cs, project, projectVm, timelineVm, details).create()
                    panel.setLayoutAndComponent(BorderLayout(), timeline)
                    cancel()
                  }, { error ->
                    val errorStatusPresenter = ErrorStatusPresenter.simple(GithubBundle.message("cannot.load.details"),
                                                                           descriptionProvider = GHHtmlErrorPanel::getLoadingErrorText)
                    val errorPanel = ErrorStatusPanelFactory.create(error, errorStatusPresenter)
                    panel.setLayoutAndComponent(SingleComponentCenteringLayout(), errorPanel)
                  })
        }
      }
    }
    return panel
  }

  private fun JPanel.setLayoutAndComponent(newLayout: LayoutManager, component: JComponent) {
    removeAll()
    layout = newLayout
    add(component)
    revalidate()
    repaint()
  }
}