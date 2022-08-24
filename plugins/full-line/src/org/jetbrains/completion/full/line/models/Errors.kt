package org.jetbrains.completion.full.line.models

data class RequestError(val error: ServerError) : Exception()

data class ServerError(
  val code: Int,
  val issue: ServerIssue,
  val message: String,
  val type: String
)

data class ServerIssue(
  val message: String,
  val validator: String,
  val value: String
)
