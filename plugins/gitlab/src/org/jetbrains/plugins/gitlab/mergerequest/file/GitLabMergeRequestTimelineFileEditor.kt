// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.async.DisposingMainScope
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.error.GitLabMergeRequestErrorStatusPresenter
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineComponentFactory
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

internal class GitLabMergeRequestTimelineFileEditor(private val project: Project, private val file: GitLabMergeRequestTimelineFile)
  : UserDataHolderBase(), FileEditor, CheckedDisposable {

  private var disposed = false
  private val propertyChangeSupport = PropertyChangeSupport(this)

  private val cs = DisposingMainScope(this)

  private val component = run {
    val wrapper = Wrapper(LoadingLabel().apply {
      border = JBUI.Borders.empty(CodeReviewChatItemUIUtil.ComponentType.FULL.paddingInsets)
    })

    cs.launchNow {
      project.serviceAsync<GitLabToolWindowViewModel>().projectVm.collectLatest { projectVm ->
        if (projectVm == null) {
          return@collectLatest
        }

        projectVm.getTimelineViewModel(file.mergeRequestId).collectLatest {
          coroutineScope {
            it.fold(
              onSuccess = {
                val timeline = GitLabMergeRequestTimelineComponentFactory.create(project, this, it, projectVm.avatarIconProvider)
                wrapper.setContent(timeline)
                wrapper.repaint()
              },
              onFailure = { error ->
                val errorPresenter = GitLabMergeRequestErrorStatusPresenter(projectVm.accountVm)
                val errorPanel = ErrorStatusPanelFactory.create(this, flowOf(error), errorPresenter).let {
                  CollaborationToolsUIUtil.moveToCenter(it)
                }
                wrapper.setContent(errorPanel)
                wrapper.repaint()
              }
            )
            try {
              awaitCancellation()
            }
            catch (e: Exception) {
              withContext(NonCancellable) {
                wrapper.setContent(null)
              }
            }
          }
        }
      }
    }
    wrapper
  }

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun getName(): String = file.presentableName
  override fun getFile(): VirtualFile = file

  override fun isValid(): Boolean = !disposed
  override fun isDisposed(): Boolean = disposed
  override fun dispose() {
    propertyChangeSupport.firePropertyChange(FileEditor.PROP_VALID, true, false)
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) =
    propertyChangeSupport.addPropertyChangeListener(listener)

  override fun removePropertyChangeListener(listener: PropertyChangeListener) =
    propertyChangeSupport.removePropertyChangeListener(listener)

  //
  // Unused
  //
  override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
  override fun setState(state: FileEditorState) {}
  override fun isModified(): Boolean = false
}
