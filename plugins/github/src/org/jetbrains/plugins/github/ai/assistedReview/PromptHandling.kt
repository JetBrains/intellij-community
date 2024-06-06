// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import com.intellij.ml.llm.context.addLineNumbers
import org.jetbrains.plugins.github.ai.GithubAIBundle

fun buildPrompt(vararg promptComponents: String): String = promptComponents.joinToString("\n")

fun extractJsonFromResponse(response: String): String {
  val extractJsonRegex = Regex("```json\n([\\s\\S]*?)\n```")
  val matches = extractJsonRegex.findAll(response)
  val jsonString = matches.firstOrNull()?.value ?: response
  val openSymbol = jsonString.indexOfFirst { it == '[' || it == '{' }
  val closeSymbol = jsonString.indexOfLast { it == ']' || it == '}' }
  if (openSymbol != -1 && closeSymbol != -1) {
    return jsonString.substring(openSymbol, closeSymbol + 1)
  }
  return jsonString
}

fun populateLineNumbers(content: String?) = content?.let { addLineNumbers(it) }

object ReviewBuddyPrompts {
  fun summarize(mergeRequestContent: String): String = buildPrompt(
    GithubAIBundle.message("review.buddy.llm.summary.task"),
    GithubAIBundle.message("review.buddy.llm.summary.guideline"),
    GithubAIBundle.message("review.buddy.llm.summary.output.format"),
    GithubAIBundle.message("review.buddy.llm.merge.request.before.after", mergeRequestContent), // Formatting string
  )

  fun sortFiles(listOfFileNames: List<String>): String = buildPrompt(
    GithubAIBundle.message("review.buddy.llm.sort.files.task"),
    GithubAIBundle.message("review.buddy.llm.sort.files.guideline"),
    GithubAIBundle.message("review.buddy.llm.list.of.files", listOfFileNames),
    GithubAIBundle.message("review.buddy.llm.sort.files.output.format"),
  )

  fun fileReview(filename: String, codeBefore: String, codeAfter: String) = buildPrompt(
    GithubAIBundle.message("review.buddy.llm.comment.file.developer.task", filename),
    GithubAIBundle.message("review.buddy.llm.comment.file.developer.guideline"),
    GithubAIBundle.message("review.buddy.llm.developer.comments.examples"),
    GithubAIBundle.message("review.buddy.llm.comment.output.format"),
    GithubAIBundle.message("review.buddy.llm.file.before.after", codeBefore, codeAfter),
  )

  fun fileReviewGuide(filename: String, codeBefore: String, codeAfter: String) = buildPrompt(
    GithubAIBundle.message("review.buddy.llm.comment.file.reviewer.task", filename),
    GithubAIBundle.message("review.buddy.llm.comment.file.reviewer.guideline"),
    GithubAIBundle.message("review.buddy.llm.reviewer.comments.examples"),
    GithubAIBundle.message("review.buddy.llm.comment.output.format"),
    GithubAIBundle.message("review.buddy.llm.file.before.after", codeBefore, codeAfter),
  )

  fun allFileReviewGuides() = buildPrompt(
    GithubAIBundle.message("review.buddy.llm.comment.all.files.reviewer.task"),
    GithubAIBundle.message("review.buddy.llm.comment.file.reviewer.guideline"),
    GithubAIBundle.message("review.buddy.llm.reviewer.comments.examples"),
    GithubAIBundle.message("review.buddy.llm.comment.output.format.all"),
  )

  fun modelReplyToContinue() = buildPrompt(
    GithubAIBundle.message("review.buddy.llm.model.reply")
  )

  fun summaryStub(summary: String) = buildPrompt(
    GithubAIBundle.message("review.buddy.llm.summary.stub", summary),
  )

  fun discussionComment(lineNumber: Int, question: String) = buildPrompt(
    GithubAIBundle.message("review.buddy.llm.discussion.comment.task", lineNumber, question),
    GithubAIBundle.message("review.buddy.llm.discussion.comment.guideline"),
  )

  fun discussionSummarize() = buildPrompt(
    GithubAIBundle.message("review.buddy.llm.discussion.summarize.task")
  )
}
