// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.async.CompletableFutureUtil.handleOnEdt
import com.intellij.diff.util.FileEditorBase
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsManager.TOPIC
import com.intellij.openapi.project.Project
import com.intellij.ui.AnimatedIcon
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRFileEditorComponentFactory
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class GHPRTimelineFileEditor(private val project: Project,
                                      private val dataContext: GHPRDataContext,
                                      private val dataProvider: GHPRDataProvider,
                                      private val file: GHRepoVirtualFile)
  : FileEditorBase() {

  val securityService = dataContext.securityService
  val repositoryDataService = dataContext.repositoryDataService
  val avatarIconsProvider = dataContext.avatarIconsProvider

  val detailsData = dataProvider.detailsData
  val reviewData = dataProvider.reviewData
  val commentsData = dataProvider.commentsData

  val timelineLoader = dataProvider.acquireTimelineLoader(this)

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

      val prevProvider = DataManager.getDataProvider(it)
      DataManager.registerDataProvider(it) { dataId ->
        when {
          GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER.`is`(dataId) -> dataProvider
          else -> prevProvider?.getData(dataId)
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
    return detailsData.loadedDetails ?: dataContext.listLoader.loadedData.find { it.id == dataProvider.id.id }
  }

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun selectNotify() {
    if (timelineLoader.loadedData.isNotEmpty())
      timelineLoader.loadMore(true)
  }

  override fun getFile() = file
}