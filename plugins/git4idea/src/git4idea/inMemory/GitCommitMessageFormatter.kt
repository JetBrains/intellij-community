// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.config.GitConfigUtil

/**
 * Formatting of the message, according to the --cleanup=<mode>
 * git commit-tree doesn't respect it, so we have to do it ourselves.
 */
object GitCommitMessageFormatter {
  fun format(project: Project, root: VirtualFile, message: String): String {
    val mode = GitConfigUtil.getCommitMessageCleanupModeCached(project, root)
    if (mode == CleanupMode.NONE) return message

    if (mode == CleanupMode.ALL) {
      val commentChar = GitConfigUtil.getCommitMessageCommentCharCached(project, root)
      return cleanupMessage(message, commentChar)
    }
    return cleanupMessage(message, null)
  }

  /**
   * Skips every line that starts with [commentChar]
   * Removes blank lines at the beginning and end of the message
   * as well as trailing blanks from every line
   * Multiple consecutive blank lines between non-empty lines are replaced with one blank line
   * Adds newline at the end of the last line if needed
   */
  private fun cleanupMessage(message: String, commentChar: String?): String {
    val lines = message.lines()

    val startIndex = lines.indexOfFirst { it.isNotBlank() && !it.isCommentLine(commentChar) }
    if (startIndex == -1) return ""

    val endIndex = lines.indexOfLast { it.isNotBlank() && !it.isCommentLine(commentChar) }

    val result = StringBuilder()
    var previousLineEmpty = false
    for (i in startIndex..endIndex) {
      val line = lines[i].trimEnd()
      if (line.isCommentLine(commentChar)) {
        continue
      }
      if (line.isEmpty()) {
        if (!previousLineEmpty) {
          result.append('\n')
        }
        previousLineEmpty = true
        continue
      }
      if (result.isNotEmpty()) {
        result.append('\n')
      }
      result.append(line)
      previousLineEmpty = false
    }
    result.append('\n')
    return result.toString()
  }

  private fun String.isCommentLine(commentChar: String?): Boolean {
    return commentChar != null && this.startsWith(commentChar)
  }

  enum class CleanupMode {
    SPACE,
    NONE,
    @Suppress("unused")
    SCISSORS, // Not used, as we don't open a commit message in the editor.'
    ALL;

    companion object {
      @JvmStatic
      fun parse(value: String?): CleanupMode =
        parseOrNull(value) ?: throw IllegalArgumentException("Invalid commit message cleanup mode '$value'")

      /**
       * Returns the cleanup mode that corresponds to the argument,
       * assuming the message is not open in the editor
       */
      private fun parseOrNull(value: String?): CleanupMode? {
        if (value == null) return SPACE

        return when (value) {
          "default" -> SPACE
          "verbatim" -> NONE
          "strip" -> ALL
          "scissors" -> SPACE // scissors behave as SPACE when a message is not open in the editor
          else -> null
        }
      }
    }
  }
}