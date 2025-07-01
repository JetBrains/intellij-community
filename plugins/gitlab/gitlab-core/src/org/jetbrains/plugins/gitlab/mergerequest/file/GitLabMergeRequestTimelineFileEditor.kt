// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginSource
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabConnectedProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineComponentFactory
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineViewModel
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestErrorUtil
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

internal class GitLabMergeRequestTimelineFileEditor(private val project: Project, private val file: GitLabMergeRequestTimelineFile)
  : UserDataHolderBase(), FileEditor, CheckedDisposable {

  private var disposed = false
  private val propertyChangeSupport = PropertyChangeSupport(this)

  private val lazyComponent by lazy {
    project.service<GitLabMergeRequestTimelineEditorFactory>().createComponent(file.mergeRequestId, this)
  }

  override fun getComponent(): JComponent = lazyComponent

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun getName(): String = file.presentableName
  override fun getFile(): VirtualFile = file

  override fun isValid(): Boolean = !disposed
  override fun isDisposed(): Boolean = disposed
  override fun dispose() {
    propertyChangeSupport.firePropertyChange(FileEditor.getPropValid(), true, false)
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

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class GitLabMergeRequestTimelineEditorFactory(private val project: Project, parentCs: CoroutineScope) {
  private val cs = parentCs.childScope(javaClass.name, Dispatchers.Main)

  internal fun createComponent(mergeRequestId: String, disposable: Disposable): JComponent {
    return createIn(project, cs.childScope("GitLabMergeRequestTimelineEditorComponent").cancelledWith(disposable), mergeRequestId)
  }

  companion object {
    fun createIn(project: Project, cs: CoroutineScope, mergeRequestId: String): JComponent {
      val wrapper = Wrapper(LoadingLabel().apply {
        border = JBUI.Borders.empty(CodeReviewChatItemUIUtil.ComponentType.FULL.paddingInsets)
      })

      cs.launchNow {
        project.serviceAsync<GitLabProjectViewModel>().connectedProjectVm.collectScoped { projectVm ->
          projectVm?.getTimelineViewModel(mergeRequestId)?.collectScoped {
            showTimelineOrError(project, projectVm, it, mergeRequestId, wrapper)
          }
        }
      }
      return wrapper
    }

    private suspend fun showTimelineOrError(
      project: Project,
      projectVm: GitLabConnectedProjectViewModel,
      timelineVmResult: Result<GitLabMergeRequestTimelineViewModel>,
      mergeRequestId: String,
      wrapper: Wrapper,
    ) {
      withContext(Dispatchers.Main.immediate) {
        timelineVmResult.fold(
          onSuccess = {
            val timeline = GitLabMergeRequestTimelineComponentFactory.create(project, this, it, projectVm.avatarIconProvider)
            wrapper.setContent(timeline)
            wrapper.repaint()
          },
          onFailure = { error ->
            val errorPresenter = GitLabMergeRequestErrorUtil.createErrorStatusPresenter(
              projectVm.accountVm,
              swingAction(GitLabBundle.message("merge.request.reload")) {
                projectVm.reloadMergeRequestDetails(mergeRequestId)
              },
              GitLabLoginSource.MR_TIMELINE)
            val errorPanel = ErrorStatusPanelFactory.create(error, errorPresenter).let {
              CollaborationToolsUIUtil.moveToCenter(it)
            }
            wrapper.setContent(errorPanel)
            wrapper.repaint()
          }
        )
        try {
          awaitCancellation()
        }
        finally {
          wrapper.setContent(null)
        }
      }
    }
  }
}
