// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.vcs

import com.intellij.grazie.text.CommitMessageDetector
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.psi.PsiElement

internal class VcsCommitMessageDetector : CommitMessageDetector {
  override fun belongsToCommitMessage(element: PsiElement): Boolean = CommitMessage.isCommitMessage(element)
}
