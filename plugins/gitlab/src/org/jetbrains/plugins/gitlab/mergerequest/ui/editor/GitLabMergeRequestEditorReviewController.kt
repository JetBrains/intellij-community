// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterControlsRenderer
import com.intellij.collaboration.ui.codereview.editor.controlInlaysIn
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

@OptIn(ExperimentalCoroutinesApi::class)
@Service(Service.Level.PROJECT)
internal class GitLabMergeRequestEditorReviewController(private val project: Project, private val cs: CoroutineScope) {

  internal class InstallerListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
      val editor = event.editor
      editor.project?.service<GitLabMergeRequestEditorReviewController>()?.setupReview(editor)
    }
  }

  private fun setupReview(editor: Editor) {
    if (editor !is EditorEx) return
    if (!isPotentialEditor(editor)) return
    val file = editor.virtualFile ?: return

    val editorDisposable = Disposer.newDisposable().also {
      EditorUtil.disposeWithEditor(editor, it)
    }

    cs.launchNow(Dispatchers.Main) {
      project.service<GitLabToolWindowViewModel>().projectVm
        .flatMapLatest {
          it?.currentMergeRequestReviewVm ?: flowOf(null)
        }.collectLatest { reviewVm ->
          reviewVm?.getFileVm(file)?.collectLatest { fileVm ->
            if (fileVm != null) {
              editor.putUserData(GitLabMergeRequestEditorReviewViewModel.KEY, reviewVm)
              try {
                val enabledFlow = reviewVm.discussionsViewOption.map { it != DiscussionsViewOption.DONT_SHOW }.distinctUntilChanged()
                val syncedFlow = reviewVm.localRepositorySyncStatus.map { it?.incoming != true }.distinctUntilChanged()
                combine(enabledFlow, syncedFlow) { enabled, synced -> enabled && synced }.collectLatest { enabled ->
                  if (enabled) supervisorScope {
                    val model = GitLabMergeRequestEditorReviewUIModel(this, fileVm, editor.document)
                    editor.putUserData(GitLabMergeRequestEditorReviewUIModel.KEY, model)
                    try {
                      showGutterMarkers(model, editor)
                      CodeReviewEditorGutterControlsRenderer.setupIn(cs, model, editor)
                      editor.controlInlaysIn(cs, model.inlays, { it.key }) { createRenderer(model, it) }
                      awaitCancellation()
                    }
                    finally {
                      editor.putUserData(GitLabMergeRequestEditorReviewUIModel.KEY, null)
                      Disposer.dispose(model)
                    }
                  }
                }
              }
              finally {
                editor.putUserData(GitLabMergeRequestEditorReviewViewModel.KEY, null)
              }
            }
          }
        }
    }.cancelOnDispose(editorDisposable)
  }

  private fun CoroutineScope.showGutterMarkers(model: GitLabMergeRequestEditorReviewUIModel, editor: Editor) {
    val disposable = Disposer.newDisposable()
    val renderer = GitLabMergeRequestReviewChangesGutterRenderer(model, editor, disposable)

    launchNow {
      try {
        model.shiftedReviewRanges.collect {
          renderer.scheduleUpdate()
        }
      }
      finally {
        Disposer.dispose(disposable)
      }
    }
  }

  private fun CoroutineScope.createRenderer(model: GitLabMergeRequestEditorReviewUIModel,
                                            inlayModel: GitLabMergeRequestEditorMappedComponentModel) =
    when (inlayModel) {
      is GitLabMergeRequestEditorMappedComponentModel.Discussion<*> ->
        GitLabMergeRequestDiscussionInlayRenderer(this, project, inlayModel.vm, model.avatarIconsProvider,
                                                  GitLabStatistics.MergeRequestNoteActionPlace.EDITOR)
      is GitLabMergeRequestEditorMappedComponentModel.DraftNote<*> ->
        GitLabMergeRequestDraftNoteInlayRenderer(this, project, inlayModel.vm, model.avatarIconsProvider,
                                                 GitLabStatistics.MergeRequestNoteActionPlace.EDITOR)
      is GitLabMergeRequestEditorMappedComponentModel.NewDiscussion<*> ->
        GitLabMergeRequestNewDiscussionInlayRenderer(this, project, inlayModel.vm, model.avatarIconsProvider,
                                                     GitLabStatistics.MergeRequestNoteActionPlace.EDITOR, inlayModel::cancel)

    }

  companion object {
    fun isPotentialEditor(editor: Editor): Boolean = editor.editorKind == EditorKind.MAIN_EDITOR && editor.virtualFile != null
  }
}

