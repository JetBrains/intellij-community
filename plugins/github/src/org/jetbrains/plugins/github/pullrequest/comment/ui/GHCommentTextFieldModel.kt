// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.async.CompletableFutureUtil.completionOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.errorOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldModelBase
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import java.util.concurrent.CompletableFuture
import javax.swing.Action

class GHCommentTextFieldModel(
  project: Project,
  initialText: String,
  private val submitter: (String) -> CompletableFuture<*>
) : CommentTextFieldModelBase(project, initialText.filter { it != '\r' }) {

  constructor(project: Project, submitter: (String) -> CompletableFuture<*>) : this(project, "", submitter)

  val errorValue: SingleValueModel<Throwable?> = SingleValueModel(null)

  override fun submit() {
    if (isBusyValue.value) return

    isBusyValue.value = true
    content.isReadOnly = true
    submitter(content.text).successOnEdt {
      content.isReadOnly = false
      content.clear()
    }.errorOnEdt {
      content.isReadOnly = false
      errorValue.value = it
    }.completionOnEdt {
      isBusyValue.value = false
    }
  }
}

fun GHCommentTextFieldModel.submitAction(name: @Nls String): Action {
  val submitAction = swingAction(name) {
    submit()
  }
  val submitEnabledListener: () -> Unit = {
    submitAction.isEnabled = !isBusyValue.value && content.text.isNotBlank()
  }
  isBusyValue.addListener {
    submitEnabledListener()
  }
  document.addDocumentListener(object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      submitEnabledListener()
    }
  })
  submitEnabledListener()
  return submitAction
}