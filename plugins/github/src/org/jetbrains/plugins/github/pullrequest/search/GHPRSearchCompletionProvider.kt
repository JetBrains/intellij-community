// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.search

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.progress.util.ProgressIndicatorUtils.awaitWithCheckCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.TextFieldCompletionProviderDumbAware
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.request.search.GithubIssueSearchSort
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery.QualifierName.*
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService

class GHPRSearchCompletionProvider(project: Project, private val repositoryDataService: GHPRRepositoryDataService)
  : TextFieldCompletionProviderDumbAware(true) {

  private val addColonInsertHandler = object : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
      if (context.completionChar == ':') return
      val editor = context.editor
      if (!isAtColon(context)) {
        EditorModificationUtil.insertStringAtCaret(editor, ":")
        context.commitDocument()
      }
      AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null)
    }

    private fun isAtColon(context: InsertionContext): Boolean {
      val startOffset = context.startOffset
      val document = context.document
      return document.textLength > startOffset && document.charsSequence[startOffset] == ':'
    }
  }

  override fun addCompletionVariants(text: String, offset: Int, prefix: String, result: CompletionResultSet) {
    val qualifierName = getCurrentQualifierName(text, offset)
    if (qualifierName == null) {
      if (prefix == EXCLUDE) {
        addExcludable(result.withPrefixMatcher(PrefixMatcher.ALWAYS_TRUE))
      }
      else {
        addExcludable(result)
        addUnexcludable(result)
      }
    }
    else {
      addValues(result, qualifierName)
    }
  }

  private fun addExcludable(result: CompletionResultSet) {
    with(result) {
      addElement(createLookupElement(state))
      addElement(createLookupElement(author, TYPE_USER))
      addElement(createLookupElement(assignee, TYPE_USER))
      addElement(createLookupElement(reviewedBy, TYPE_USER))
      addElement(createLookupElement(reviewRequested, TYPE_USER))
      addElement(createLookupElement(label, TYPE_LABEL))
    }
  }

  private fun addUnexcludable(result: CompletionResultSet) {
    with(result) {
      addElement(createLookupElement(after, TYPE_DATE))
      addElement(createLookupElement(before, TYPE_DATE))
      addElement(createLookupElement(sortBy))
    }
  }

  private fun addValues(result: CompletionResultSet, qualifierName: String) {
    when {
      qualifierName.equals(assignee.name, true) -> {
        for (user in awaitWithCheckCanceled(repositoryDataService.issuesAssignees)) {
          result.addElement(user.login)
        }
        result.addElement(USER_ME)
      }
      qualifierName.equals(author.name, true) -> {
        for (user in awaitWithCheckCanceled(repositoryDataService.collaborators)) {
          result.addElement(user.login)
        }
        result.addElement(USER_ME)
      }
      qualifierName.equals(label.name, true) -> {
        for (label in awaitWithCheckCanceled(repositoryDataService.labels)) {
          val quoted = label.name.let {
            if (StringUtil.containsWhitespaces(it)) StringUtil.wrapWithDoubleQuote(it) else it
          }
          result.addElement(LookupElementBuilder.create(quoted))
        }
      }
      qualifierName.equals(reviewedBy.toString(), true) ||
      qualifierName.equals(reviewRequested.toString(), true) -> {
        for (user in awaitWithCheckCanceled(repositoryDataService.potentialReviewers)) {
          result.addElement(user.shortName)
        }
        result.addElement(USER_ME)
      }
      qualifierName.equals(state.name, true) -> {
        for (state in GithubIssueState.values()) {
          result.addElement(state.name)
        }
      }
      qualifierName.equals(sortBy.name, true) -> {
        for (sort in GithubIssueSearchSort.values()) {
          result.addElement(sort.name)
        }
      }
    }
  }

  /**
   * Prefix is the char sequence from last space or first colon after space/line start to caret
   */
  override fun getPrefix(currentTextPrefix: String): String {
    val spaceIdx = currentTextPrefix.lastIndexOf(' ')
    val colonIdx = currentTextPrefix.indexOf(':', spaceIdx.coerceAtLeast(0))
    return currentTextPrefix.substring(spaceIdx.coerceAtLeast(colonIdx) + 1)
  }

  /**
   * Current qualifier name is the nearest char sequence in between space and colon or before first colon excluding leading minus
   * "qname:test" -> "qname"
   * "qname:test:test" -> "qname"
   * " qname:test:test" -> "qname"
   * " -qname:test:test" -> "qname"
   * " qname:test:test " -> null
   */
  private fun getCurrentQualifierName(text: String, offset: Int): String? {
    val spaceIdx = text.lastIndexOf(' ', offset - 1)
    val colonIdx = text.indexOf(':', spaceIdx.coerceAtLeast(0))
    if (colonIdx < 0 || spaceIdx > colonIdx) return null
    return text.substring(spaceIdx + 1, colonIdx).removePrefix(EXCLUDE)
  }

  private fun createLookupElement(lookupObject: Any, typeText: String? = null): LookupElementBuilder {
    return LookupElementBuilder.create(lookupObject)
      .withTailText(":")
      .withInsertHandler(addColonInsertHandler)
      .let { if (typeText != null) it.withTypeText(typeText, true) else it }
  }

  private fun CompletionResultSet.addElement(variant: Any) = addElement(LookupElementBuilder.create(variant))

  companion object {
    private const val EXCLUDE = "-"
    private const val TYPE_USER = "username"
    private const val TYPE_DATE = "YYYY-MM-DD"
    private const val TYPE_LABEL = "label name"
    private const val USER_ME = "@me"
  }
}
