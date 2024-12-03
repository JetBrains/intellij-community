// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.LoadingTextLabel
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.util.getOrNull
import com.intellij.diff.util.FileEditorBase
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsManager.TOPIC
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.SingleComponentCenteringLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsFull
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRFileEditorComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowProjectViewModel
import org.jetbrains.plugins.github.ui.component.GHHtmlErrorPanel
import java.awt.BorderLayout
import java.awt.LayoutManager
import javax.swing.JComponent
import javax.swing.JPanel

internal class GHPRTimelineFileEditor(parentCs: CoroutineScope,
                                      private val projectVm: GHPRToolWindowProjectViewModel,
                                      private val file: GHPRTimelineVirtualFile)
  : FileEditorBase() {
  private val cs = parentCs
    .childScope("GitHub Pull Request Timeline UI", Dispatchers.Main)
    .cancelledWith(this)

  private val timelineVm = projectVm.acquireTimelineViewModel(file.pullRequest, this)

  override fun getName() = GithubBundle.message("pull.request.editor.timeline")

  private val content by lazy(LazyThreadSafetyMode.NONE, ::createContent)

  override fun getComponent() = content

  private fun createContent(): JComponent {
    return doCreateContent().apply {
      isOpaque = true
      background = EditorColorsManager.getInstance().globalScheme.defaultBackground
    }.also {
      ApplicationManager.getApplication().messageBus.connect(this)
        .subscribe(TOPIC, EditorColorsListener { scheme -> it.background = scheme?.defaultBackground })

      DataManager.registerDataProvider(it) { dataId ->
        when {
          GHPRActionKeys.PULL_REQUEST_ID.`is`(dataId) -> file.pullRequest
          GHPRActionKeys.PULL_REQUEST_URL.`is`(dataId) -> timelineVm.detailsVm.details.value.getOrNull()?.url
          else -> null
        }
      }
    }
  }

  private fun doCreateContent(): JComponent {
    val panel = JBPanel<JBPanel<*>>()
    cs.launchNow {
      timelineVm.detailsVm.details.collectLatest {
        when (val result = it.result) {
          null -> panel.setLayoutAndComponent(SingleComponentCenteringLayout(), LoadingTextLabel())
          else -> result
            .fold({ details ->
                    //further updates will be handled by the timeline itself
                    panel.showTimeline(details)
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

  private fun JPanel.showTimeline(details: GHPRDetailsFull) {
    val timeline = GHPRFileEditorComponentFactory(cs, file.project, projectVm, timelineVm, details).create()
    setLayoutAndComponent(BorderLayout(), timeline)
  }

  private fun JPanel.setLayoutAndComponent(newLayout: LayoutManager, component: JComponent) {
    removeAll()
    layout = newLayout
    add(component)
    revalidate()
    repaint()
  }

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun selectNotify() {
    timelineVm.update()
  }

  override fun getFile() = file
}