// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import one.util.streamex.StreamEx

internal class RecentMessagesCommitCompletionContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val file = parameters.originalFile
    val project = file.project
    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
    if (!CommitMessage.isCommitMessage(document)) return
    if (parameters.invocationCount == 0) return

    result.caseInsensitive()
      .withPrefixMatcher(PlainPrefixMatcher(TextFieldWithAutoCompletionListProvider.getCompletionPrefix(parameters)))
      .addAllElements(
        StreamEx.of(VcsConfiguration.getInstance(project).recentMessages)
          .reverseSorted()
          .map { lookupString: String ->
            PrioritizedLookupElement.withPriority(LookupElementBuilder.create(lookupString), Int.MIN_VALUE.toDouble())
          })
  }
}
