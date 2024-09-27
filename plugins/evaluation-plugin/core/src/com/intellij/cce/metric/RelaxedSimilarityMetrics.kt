package com.intellij.cce.metric

object RelaxedSimilarityUtils {
  private val nonValuable = Regex("[^a-zA-Z0-9_+\\-*%=&|@\$?]")

  private fun String.rightSplit(on: String, default: Pair<String, String>): Pair<String, String> {
    val index = lastIndexOf(on)
    if (index == -1) return default
    return substring(0, index) to substring(index + 1)
  }

  private fun String.leftSplit(on: String, default: Pair<String, String>): Pair<String, String> {
    val index = indexOf(on)
    if (index == -1) return default
    return substring(0, index) to substring(index + 1)
  }

  fun preProcessLines(completion: String, prefix: String, suffix: String, stripChars: Boolean = true): List<String> {
    var completionWithPrefix = completion

    if (!prefix.endsWith("\n") && !completion.startsWith("\n")) {
      val (_, startedLine) = prefix.rightSplit("\n", default = "" to prefix)
      completionWithPrefix = startedLine + completion
    }
    if (!completion.endsWith("\n") && !suffix.startsWith("\n")) {
      val (startedLine, _) = suffix.leftSplit("\n", default = "" to suffix)
      completionWithPrefix += startedLine
    }

    val lines = completionWithPrefix.split("\n")
    return lines.map { line ->
      if (stripChars) nonValuable.replace(line, "") else line
    }.filter { it.isNotBlank() }
  }
}