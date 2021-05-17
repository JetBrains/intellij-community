// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.async.CompletableFutureUtil.completionOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.errorOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.ui.codereview.timeline.comment.SubmittableTextFieldModelBase
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.ui.LanguageTextField
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import java.util.concurrent.CompletableFuture

open class GHSubmittableTextFieldModel(
  final override val project: Project,
  initialText: String,
  private val submitter: (String) -> CompletableFuture<*>
) : SubmittableTextFieldModelBase(initialText) {

  constructor(project: Project, submitter: (String) -> CompletableFuture<*>) : this(project, "", submitter)

  override val document: Document = LanguageTextField.createDocument(
    initialText,
    MarkdownLanguage.INSTANCE,
    project,
    LanguageTextField.SimpleDocumentCreator()
  )

  override fun submit() {
    if (isBusy) return

    isBusy = true
    document.setReadOnly(true)
    submitter(document.text).successOnEdt {
      document.setReadOnly(false)
      runWriteAction {
        document.setText("")
      }
    }.errorOnEdt {
      document.setReadOnly(false)
      error = it
    }.completionOnEdt {
      isBusy = false
    }
  }
}