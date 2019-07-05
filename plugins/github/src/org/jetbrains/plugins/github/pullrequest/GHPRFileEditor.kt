// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.data.GHPRTimelineLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRHeaderPanel
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineComponent
import org.jetbrains.plugins.github.ui.GHListLoaderPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.handleOnEdt
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent
import javax.swing.JPanel

internal class GHPRFileEditor(private val file: GHPRVirtualFile)
  : UserDataHolderBase(), FileEditor {

  private val propertyChangeSupport = PropertyChangeSupport(this)
  private val panel: JPanel

  init {
    val detailsModel = SingleValueModel(file.pullRequest)

    val dataProvider = file.dataProvider
    dataProvider.addRequestsChangesListener(this, object : GithubPullRequestDataProvider.RequestsChangedListener {
      override fun detailsRequestChanged() {
        dataProvider.detailsRequest.handleOnEdt(this@GHPRFileEditor) { pr, _ ->
          if (pr != null) detailsModel.value = pr
        }
      }
    })

    val header = GHPRHeaderPanel(detailsModel)
    Disposer.register(this, header)

    val timelineListModel = CollectionListModel<GHPRTimelineItem>()
    val timeline = GHPRTimelineComponent(timelineListModel)
    val loadingIcon = AsyncProcessIcon("Loading").apply {
      isVisible = false
    }

    val context = file.dataContext
    val loader = GHPRTimelineLoader(service(), context.requestExecutor, context.serverPath, context.repositoryDetails.fullPath,
                                    file.pullRequest.number, timelineListModel)
    Disposer.register(this, loader)

    val contentPanel = object : ScrollablePanel(), ComponentWithEmptyText, Disposable {
      init {
        isOpaque = false
        border = JBUI.Borders.empty(UIUtil.LARGE_VGAP, UIUtil.DEFAULT_HGAP * 2)

        layout = BorderLayout()

        add(header, BorderLayout.NORTH)
        add(timeline, BorderLayout.CENTER)
        add(loadingIcon, BorderLayout.SOUTH)

        emptyText.clear()
      }

      override fun getEmptyText() = timeline.emptyText

      override fun dispose() {}
    }
    Disposer.register(contentPanel, loadingIcon)

    panel = object : GHListLoaderPanel<GHPRTimelineLoader>(loader, contentPanel, true) {
      override val loadingText = ""

      init {
        background = UIUtil.getListBackground()
      }

      override fun createCenterPanel(content: JComponent) = Wrapper(content)

      override fun setLoading(isLoading: Boolean) {
        loadingIcon.isVisible = isLoading
      }
    }
    Disposer.register(panel, contentPanel)
    Disposer.register(this, panel)
  }

  override fun getName() = file.name

  override fun getComponent(): JComponent = panel
  override fun getPreferredFocusedComponent(): JComponent? = panel

  override fun getFile() = file
  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true

  override fun selectNotify() {}
  override fun deselectNotify() {}

  override fun addPropertyChangeListener(listener: PropertyChangeListener) = propertyChangeSupport.addPropertyChangeListener(listener)
  override fun removePropertyChangeListener(listener: PropertyChangeListener) = propertyChangeSupport.removePropertyChangeListener(listener)

  override fun setState(state: FileEditorState) {}
  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null

  override fun getCurrentLocation(): FileEditorLocation? = null

  override fun dispose() {}
}
