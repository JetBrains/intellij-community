// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.rw

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiDocumentManagerBase

internal class CommittedDocumentsConstraint(
  private val project: Project,
) : ReadConstraint {

  override fun isSatisfied(): Boolean {
    val manager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase
    return !manager.isCommitInProgress && !manager.hasEventSystemEnabledUncommittedDocuments()
  }

  override fun schedule(runnable: Runnable) {
    PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(ModalityState.any(), runnable)
  }
}
