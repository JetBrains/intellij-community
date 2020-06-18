// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.diff.util.FileEditorBase
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.ui.AnimatedIcon
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRFileEditorComponentFactory
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class GHPRTimelineFileEditor(private val project: Project,
                                      private val dataContext: GHPRDataContext,
                                      private val pullRequest: GHPRIdentifier)
  : FileEditorBase() {

  val securityService = dataContext.securityService
  val avatarIconsProviderFactory = dataContext.avatarIconsProviderFactory

  private val dataProvider = dataContext.dataProviderRepository.getDataProvider(pullRequest, this)
  val detailsData = dataProvider.detailsData
  val stateData = dataProvider.stateData
  val changesData = dataProvider.changesData
  val reviewData = dataProvider.reviewData
  val commentsData = dataProvider.commentsData

  val timelineLoader = dataProvider.acquireTimelineLoader(this)

  val remoteUrl = dataContext.gitRepositoryCoordinates

  override fun getName() = "Pull Request Timeline"

  private val content by lazy(LazyThreadSafetyMode.NONE, ::createContent)

  override fun getComponent() = content

  private fun createContent(): JComponent {
    return doCreateContent().also {
      GithubUIUtil.overrideUIDependentProperty(it) {
        isOpaque = true
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
      }

      DataManager.registerDataProvider(it) { dataId ->
        when {
          GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER.`is`(dataId) -> dataProvider
          else -> null
        }
      }
    }
  }

  private fun doCreateContent(): JComponent {
    val details = getCurrentDetails()
    if (details != null) {
      return GHPRFileEditorComponentFactory(project, this, details).create()
    }
    else {
      val panel = JPanel(SingleComponentCenteringLayout()).apply {
        add(JLabel().apply {
          foreground = UIUtil.getContextHelpForeground()
          text = ApplicationBundle.message("label.loading.page.please.wait")
          icon = AnimatedIcon.Default()
        })
      }

      detailsData.loadDetails().handleOnEdt(this) { loadedDetails, error ->
        if (loadedDetails != null) {
          panel.layout = BorderLayout()
          panel.removeAll()
          panel.add(GHPRFileEditorComponentFactory(project, this, loadedDetails).create())
        }
        else if (error != null) {
          //TODO: handle error
          throw error
        }
      }
      return panel
    }
  }


  private fun getCurrentDetails(): GHPullRequestShort? {
    return detailsData.loadedDetails ?: dataContext.listLoader.loadedData.find { it.id == pullRequest.id }
  }

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun selectNotify() {
    if (timelineLoader.loadedData.isNotEmpty())
      timelineLoader.loadMore(true)
  }
}
