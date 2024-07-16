// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

object GitSquashedCommitsMessage {
  private const val REBASE_SQUASH_SEPARATOR = "\n\n\n"
  private val AUTOSQUASH_SUBJECT_PREFIXES = listOf("fixup!", "squash!", "amend!")

  fun trimAutosquashSubject(commitMessage: String) = if (isAutosquashCommitMessage(commitMessage)) {
    commitMessage.substringAfter("\n", "").trim()
  }
  else commitMessage

  fun isAutosquashCommitMessage(commitMessage: String) = AUTOSQUASH_SUBJECT_PREFIXES.any {
    commitMessage.startsWith(it)
  }

  fun squash(message1: String, message2: String): String = message1 + REBASE_SQUASH_SEPARATOR + message2

  /**
   * Combines commits messages removing duplicates and trimming autosquash subjects (see [AUTOSQUASH_SUBJECT_PREFIXES])
   */
  fun prettySquash(messages: Collection<String>): String {
    val squashedMessage = messages.mapNotNullTo(mutableSetOf()) { message ->
      trimAutosquashSubject(message).takeIf { it.isNotEmpty() }
    }.joinToString(REBASE_SQUASH_SEPARATOR)

    // Can be blank when squashing 2 "fixup! ..." commits
    return squashedMessage.ifBlank { return messages.toSet().joinToString(REBASE_SQUASH_SEPARATOR) }
  }
}