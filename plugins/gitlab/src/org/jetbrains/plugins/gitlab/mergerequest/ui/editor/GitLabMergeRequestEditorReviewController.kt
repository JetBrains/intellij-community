// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterChangesRenderer
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterControlsRenderer
import com.intellij.collaboration.ui.codereview.editor.action.CodeReviewInEditorToolbarActionGroup
import com.intellij.collaboration.ui.codereview.editor.renderInlays
import com.intellij.collaboration.util.HashingUtil
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
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
              val toolbarActionGroup = DefaultActionGroup(
                CodeReviewInEditorToolbarActionGroup(reviewVm),
                Separator.getInstance()
              )
              val editorMarkupModel = editor.markupModel as? EditorMarkupModel
              editorMarkupModel?.addInspectionWidgetAction(toolbarActionGroup, Constraints.FIRST)
              try {
                val enabledFlow = reviewVm.discussionsViewOption.map { it != DiscussionsViewOption.DONT_SHOW }
                val syncedFlow = reviewVm.localRepositorySyncStatus.map { it?.getOrNull()?.incoming != true }
                combine(enabledFlow, syncedFlow) { enabled, synced -> enabled && synced }.distinctUntilChanged().collectLatest { enabled ->
                  if (enabled) showReview(fileVm, editor)
                }
              }
              finally {
                editorMarkupModel?.removeInspectionWidgetAction(toolbarActionGroup)
              }
            }
          }
        }
    }.cancelOnDispose(editorDisposable)
  }

  private suspend fun showReview(fileVm: GitLabMergeRequestEditorReviewFileViewModel, editor: EditorEx): Nothing {
    withContext(Dispatchers.Main) {
      val preferences = project.serviceAsync<GitLabMergeRequestsPreferences>()
      val model = GitLabMergeRequestEditorReviewUIModel(this, preferences, fileVm, editor.document)
      try {
        launchNow {
          CodeReviewEditorGutterChangesRenderer.render(model, editor)
        }
        launchNow {
          CodeReviewEditorGutterControlsRenderer.render(model, editor)
        }
        launchNow {
          editor.renderInlays(model.inlays, HashingUtil.mappingStrategy(GitLabMergeRequestEditorMappedComponentModel::key)) { createRenderer(model, it) }
        }
        awaitCancellation()
      }
      finally {
        withContext(NonCancellable) {
          Disposer.dispose(model)
        }
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
    private fun isPotentialEditor(editor: Editor): Boolean = editor.editorKind == EditorKind.MAIN_EDITOR && editor.virtualFile != null
  }
}

