// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.editor.*
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.HashingUtil
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.data.GitLabImageLoader
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestEditorReviewViewModel.FileReviewState
import org.jetbrains.plugins.gitlab.util.GitLabBundle
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
      project.service<GitLabProjectViewModel>().connectedProjectVm
        .flatMapLatest {
          it?.currentMergeRequestReviewVm ?: flowOf(null)
        }.collectLatest { reviewVm ->
          reviewVm?.getFileStateFlow(file)?.collectScoped { fileState ->
            when (fileState) {
              is FileReviewState.ReviewEnabled -> showReview(reviewVm, fileState.vm, editor)
              FileReviewState.ReviewDisabledEmptyDiff -> showEmptyDiffNotification(reviewVm, editor)
              FileReviewState.NotInReview -> return@collectScoped
            }
          }
        }
    }.cancelOnDispose(editorDisposable)
  }

  private suspend fun showReview(
    reviewVm: GitLabMergeRequestEditorReviewViewModel,
    fileVm: GitLabMergeRequestEditorReviewFileViewModel,
    editor: EditorEx,
  ): Nothing {
    supervisorScope {
      val actionManager = serviceAsync<ActionManager>()
      launchNow {
        ReviewInEditorUtil.showReviewToolbarWithActions(
          reviewVm, editor,
          actionManager.getAction("CodeReview.PreviousComment"),
          actionManager.getAction("CodeReview.NextComment"),
        )
      }

      val enabledFlow = reviewVm.discussionsViewOption.map { it != DiscussionsViewOption.DONT_SHOW }
      val syncedFlow = reviewVm.localRepositorySyncStatus.map { it?.getOrNull()?.incoming != true }
      combine(enabledFlow, syncedFlow) { enabled, synced -> enabled && synced }.distinctUntilChanged().collectLatest { enabled ->
        if (enabled) showReview(fileVm, editor)
      }
      awaitCancellation()
    }
  }

  private suspend fun showEmptyDiffNotification(reviewVm: GitLabMergeRequestEditorReviewViewModel, editor: Editor): Nothing {
    val actionManager = serviceAsync<ActionManager>()
    ReviewInEditorUtil.showReviewToolbarWithWarning(
      reviewVm, editor,
      actionManager.getAction("CodeReview.PreviousComment"),
      actionManager.getAction("CodeReview.NextComment")
    ) {
      GitLabBundle.message("merge.request.editor.empty.patch.warning")
    }
  }

  private suspend fun showReview(
    fileVm: GitLabMergeRequestEditorReviewFileViewModel,
    editor: EditorEx,
  ): Nothing {
    withContext(Dispatchers.Main) {
      val preferences = project.serviceAsync<GitLabMergeRequestsPreferences>()
      val reviewHeadContent = fileVm.headContent.mapNotNull { it?.result?.getOrThrow() }.first()

      val model = GitLabMergeRequestEditorReviewUIModel(this, preferences, fileVm) showEditor@{ changeToShow, lineIdx ->
        val file = changeToShow.filePathAfter?.virtualFile ?: return@showEditor
        val fileOpenDescriptor = OpenFileDescriptor(project, file, lineIdx, 0)
        FileEditorManager.getInstance(project).openFileEditor(fileOpenDescriptor, true)
      }

      launchNow {
        ReviewInEditorUtil.trackDocumentDiffSync(reviewHeadContent, editor.document, model::setPostReviewChanges)
      }

      launchNow {
        CodeReviewEditorGutterChangesRenderer.render(model, editor)
      }
      launchNow {
        CodeReviewEditorGutterControlsRenderer.render(model, editor)
      }
      launchNow {
        editor.renderInlays(model.inlays, HashingUtil.mappingStrategy(GitLabMergeRequestEditorMappedComponentModel::key)) {
          createRenderer(it, fileVm.avatarIconsProvider, fileVm.imageLoader)
        }
      }

      editor.putUserData(CodeReviewCommentableEditorModel.KEY, model)
      editor.putUserData(CodeReviewNavigableEditorViewModel.KEY, model)
      try {
        awaitCancellation()
      }
      finally {
        editor.putUserData(CodeReviewCommentableEditorModel.KEY, null)
        editor.putUserData(CodeReviewNavigableEditorViewModel.KEY, null)
      }
    }
  }

  private fun CoroutineScope.createRenderer(
    inlayModel: GitLabMergeRequestEditorMappedComponentModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
    imageLoader: GitLabImageLoader,
  ) =
    when (inlayModel) {
      is GitLabMergeRequestEditorMappedComponentModel.Discussion<*> ->
        GitLabMergeRequestDiscussionInlayRenderer(this, project, inlayModel.vm, avatarIconsProvider,
                                                  imageLoader,
                                                  GitLabStatistics.MergeRequestNoteActionPlace.EDITOR)
      is GitLabMergeRequestEditorMappedComponentModel.DraftNote<*> ->
        GitLabMergeRequestDraftNoteInlayRenderer(this, project, inlayModel.vm, avatarIconsProvider,
                                                 imageLoader,
                                                 GitLabStatistics.MergeRequestNoteActionPlace.EDITOR)
      is GitLabMergeRequestEditorMappedComponentModel.NewDiscussion<*> ->
        GitLabMergeRequestNewDiscussionInlayRenderer(this, project, inlayModel.vm, avatarIconsProvider,
                                                     GitLabStatistics.MergeRequestNoteActionPlace.EDITOR, inlayModel::cancel)

    }

  companion object {
    private fun isPotentialEditor(editor: Editor): Boolean = editor.editorKind == EditorKind.MAIN_EDITOR && editor.virtualFile != null
  }
}

