// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.getMarkdownLanguage
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.ui.LanguageTextField
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.swing.JComponent

object GitLabNoteEditorComponentFactory {
  fun create(project: Project, cs: CoroutineScope, vm: GitLabNoteEditingViewModel,
             actions: CommentInputActionsComponentFactory.Config,
             icon: CommentTextFieldFactory.IconConfig? = null): JComponent {
    val document = LanguageTextField.createDocument(
      "",
      getMarkdownLanguage() ?: PlainTextLanguage.INSTANCE,
      project,
      LanguageTextField.SimpleDocumentCreator()
    )

    document.bindTextIn(cs, vm.text)

    val textField = CommentTextFieldFactory.create(project, document)

    UiNotifyConnector.installOn(textField, object : Activatable {
      private var focusListenerJob: Job? = null

      override fun showNotify() {
        focusListenerJob = cs.launchNow {
          vm.focusRequests.collect {
            CollaborationToolsUIUtil.focusPanel(textField)
          }
        }
      }

      override fun hideNotify() {
        focusListenerJob?.cancel()
      }
    })

    val busyValue = vm.state.mapToValueModel(cs, false) {
      it is GitLabNoteEditingViewModel.SubmissionState.Loading
    }
    val errorValue = vm.state.mapToValueModel(cs, null) {
      (it as? GitLabNoteEditingViewModel.SubmissionState.Error)?.error?.localizedMessage
    }

    CollaborationToolsUIUtil.installValidator(textField, errorValue)
    val inputField = CollaborationToolsUIUtil.wrapWithProgressOverlay(textField, busyValue).let {
      if (icon != null) {
        CommentTextFieldFactory.wrapWithLeftIcon(icon, it)
      }
      else {
        it
      }
    }

    return CommentInputActionsComponentFactory.attachActions(cs, inputField, actions)
  }

  private fun <T, R> Flow<T>.mapToValueModel(cs: CoroutineScope, initial: R, mapper: (T) -> R): SingleValueModel<R> =
    SingleValueModel(initial).apply {
      cs.launch {
        collect {
          value = mapper(it)
        }
      }
    }
}
