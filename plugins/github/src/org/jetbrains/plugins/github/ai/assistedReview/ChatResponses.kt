// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileReviewAiResponse(
  val summary: String,
  val comments: List<ReviewCommentAiResponse>,
)

@Serializable
data class NamedFileReviewAiResponse(
  val filename: String,
  val summary: String,
  val comments: List<ReviewCommentAiResponse>,
)

@Serializable
data class ReviewCommentAiResponse(
  @SerialName("line_number")
  val lineNumber: Int,
  val reasoning: String,
  val comment: String,
)

fun FileReviewAiResponse.sorted() = FileReviewAiResponse(
  summary,
  comments.sortedBy { it.lineNumber }
)

fun NamedFileReviewAiResponse.sorted() = NamedFileReviewAiResponse(
  filename,
  summary,
  comments.sortedBy { it.lineNumber }
)
