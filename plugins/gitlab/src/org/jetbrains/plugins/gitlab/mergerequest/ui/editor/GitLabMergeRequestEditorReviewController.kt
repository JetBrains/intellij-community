// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
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
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.supervisorScope
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
              try {
                editor.putUserData(GitLabMergeRequestEditorReviewViewModel.KEY, reviewVm)
                val enabledFlow = reviewVm.discussionsViewOption.map { it != DiscussionsViewOption.DONT_SHOW }.distinctUntilChanged()
                val syncedFlow = reviewVm.localRepositorySyncStatus.map { it?.incoming != true }.distinctUntilChanged()
                combine(enabledFlow, syncedFlow) { enabled, synced -> enabled && synced }.collectLatest {
                  if (it) supervisorScope {
                    val model = GitLabMergeRequestEditorReviewUIModel(this, fileVm, editor.document)
                    editor.putUserData(GitLabMergeRequestEditorReviewUIModel.KEY, model)
                    showGutterMarkers(model, editor)
                    showGutterControls(model, editor)
                    showInlays(model, editor)
                    awaitCancellationAndInvoke {
                      editor.putUserData(GitLabMergeRequestEditorReviewUIModel.KEY, null)
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
      model.shiftedReviewRanges.collect {
        renderer.scheduleUpdate()
      }
    }

    awaitCancellationAndInvoke {
      Disposer.dispose(disposable)
    }
  }

  /**
   * Show new and existing comments as editor inlays
   * Only comments located on the right diff side are shown
   */
  private fun CoroutineScope.showInlays(model: GitLabMergeRequestEditorReviewUIModel, editor: Editor) {
    val cs = this
    editor as EditorEx

    editor.controlInlaysIn(cs, model.discussions, { it.vm.id }) {
      GitLabMergeRequestDiscussionInlayRenderer(this, project, it.vm, model.avatarIconsProvider,
                                                GitLabStatistics.MergeRequestNoteActionPlace.EDITOR)
    }

    editor.controlInlaysIn(cs, model.draftDiscussions, { it.vm.id }) {
      GitLabMergeRequestDraftNoteInlayRenderer(this, project, it.vm, model.avatarIconsProvider,
                                                GitLabStatistics.MergeRequestNoteActionPlace.EDITOR)
    }

    editor.controlInlaysIn(cs, model.newDiscussions, { "NEW_${it.vm.originalLine}" }) {
      GitLabMergeRequestNewDiscussionInlayRenderer(
        this, project, it.vm, model.avatarIconsProvider,
        GitLabStatistics.MergeRequestNoteActionPlace.EDITOR
      ) { model.cancelNewDiscussion(it.vm.originalLine) }
    }
  }

  private fun CoroutineScope.showGutterControls(model: GitLabMergeRequestEditorReviewUIModel, editor: Editor) {
    val cs = this
    editor as EditorEx

    GitLabMergeRequestReviewControlsGutterRenderer.setupIn(cs, model.nonCommentableRanges, editor) {
      model.requestNewDiscussion(it, true)
    }
  }

  companion object {
    fun isPotentialEditor(editor: Editor): Boolean = editor.editorKind == EditorKind.MAIN_EDITOR && editor.virtualFile != null
  }
}

