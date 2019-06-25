// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRHeaderPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.handleOnEdt
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

    panel = JBUI.Panels.simplePanel()
      .withMaximumWidth(maxWidth)
      .withBackground(UIUtil.getListBackground())
      .withBorder(JBUI.Borders.empty(UIUtil.LARGE_VGAP, UIUtil.DEFAULT_HGAP * 2))
      .addToTop(header)

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
