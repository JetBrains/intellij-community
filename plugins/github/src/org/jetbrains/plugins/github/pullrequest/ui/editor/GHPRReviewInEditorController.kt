// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.editor.*
import com.intellij.collaboration.util.HashingUtil
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
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
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@Service(Service.Level.PROJECT)
internal class GHPRReviewInEditorController(private val project: Project, private val cs: CoroutineScope) {
  internal class InstallerListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
      val editor = event.editor
      editor.project?.service<GHPRReviewInEditorController>()?.setupReview(editor)
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
      val settings = project.serviceAsync<GithubPullRequestsProjectUISettings>()
      project.serviceAsync<GHPRToolWindowViewModel>().projectVm
        .flatMapLatest { projectVm ->
          projectVm?.prOnCurrentBranch?.mapScoped {
            val id = it?.getOrNull() ?: return@mapScoped null
            projectVm.acquireEditorReviewViewModel(id, nestedDisposable())
          } ?: flowOf(null)
        }.collectLatest { reviewVm ->
          reviewVm?.getViewModelFor(file)?.collectScoped { fileVm ->
            if (fileVm != null) supervisorScope {
              launchNow {
                ReviewInEditorUtil.showReviewToolbar(reviewVm, editor)
              }

              val enabledFlow = reviewVm.discussionsViewOption.map { it != DiscussionsViewOption.DONT_SHOW }
              val syncedFlow = reviewVm.updateRequired.map { !it }
              combine(enabledFlow, syncedFlow) { enabled, synced -> enabled && synced }.distinctUntilChanged().collectLatest { enabled ->
                if (enabled) showReview(settings, fileVm, editor)
              }
            }
          }
        }
    }.cancelOnDispose(editorDisposable)
  }

  companion object {
    private fun isPotentialEditor(editor: Editor): Boolean = editor.editorKind == EditorKind.MAIN_EDITOR && editor.virtualFile != null
  }
}

private suspend fun showReview(settings: GithubPullRequestsProjectUISettings, fileVm: GHPRReviewFileEditorViewModel, editor: EditorEx): Nothing {
  withContext(Dispatchers.Main.immediate) {
    val reviewHeadContent = fileVm.originalContent.mapNotNull { it?.result?.getOrThrow() }.first()

    val model = GHPRReviewFileEditorModel(this, settings, fileVm)
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
      val userIcon = fileVm.iconProvider.getIcon(fileVm.currentUser.url, 16)
      editor.renderInlays(model.inlays, HashingUtil.mappingStrategy(GHPREditorMappedComponentModel::key)) { createRenderer(it, userIcon) }
    }
    editor.putUserData(CodeReviewCommentableEditorModel.KEY, model)
    try {
      awaitCancellation()
    }
    finally {
      editor.putUserData(CodeReviewCommentableEditorModel.KEY, null)
    }
  }
}
