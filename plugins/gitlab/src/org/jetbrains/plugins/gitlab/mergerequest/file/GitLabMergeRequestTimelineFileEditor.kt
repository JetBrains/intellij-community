// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.async.DisposingScope
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.diff.ChangesSelection
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectUIContext
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineComponentFactory
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.LoadAllGitLabMergeRequestTimelineViewModel
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

internal class GitLabMergeRequestTimelineFileEditor(private val project: Project,
                                                    private val ctx: GitLabProjectUIContext,
                                                    private val file: GitLabMergeRequestTimelineFile)
  : UserDataHolderBase(), FileEditor, CheckedDisposable {

  private var disposed = false
  private val propertyChangeSupport = PropertyChangeSupport(this)

  private val cs = DisposingScope(this)

  private val component = run {
    val vm = LoadAllGitLabMergeRequestTimelineViewModel(cs, ctx.currentUser, ctx.projectData, file.mergeRequestId)
    val uiCs = cs.childScope(Dispatchers.Main)

    val diffBridge = ctx.getDiffBridge(file.mergeRequestId)
    uiCs.launch(start = CoroutineStart.UNDISPATCHED) {
      vm.diffRequests.collect { (change, location) ->
        diffBridge.setChanges(ChangesSelection.Single(listOf(change), 0, location))
        diffBridge.changeSelected(change)
        ctx.filesController.openDiff(file.mergeRequestId, true)
      }
    }
    GitLabMergeRequestTimelineComponentFactory.create(project, uiCs, vm, ctx.avatarIconProvider)
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
