// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionPreselectionBehaviourProvider
import com.intellij.openapi.vcs.ui.CommitMessage.isCommitMessage

private class CommitCompletionPreselectionBehaviourProvider : CompletionPreselectionBehaviourProvider() {
  override fun shouldPreselectFirstSuggestion(parameters: CompletionParameters): Boolean =
    !isCommitMessage(parameters.originalFile)
}