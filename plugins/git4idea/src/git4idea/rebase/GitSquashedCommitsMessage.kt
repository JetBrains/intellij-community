// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase
object GitSquashedCommitsMessage {
  private const val REBASE_SQUASH_SEPARATOR = "\n\n\n"

  private val AUTOSQUASH_SUBJECT_REGEX = Regex("^(fixup|squash|amend)! (.+)$")

  fun trimAutosquashSubject(commitMessage: String): String = if (isAutosquashCommitMessage(commitMessage)) {
    removeSubject(commitMessage)
  }
  else commitMessage

  fun isAutosquashCommitMessage(commitMessage: String): Boolean = getSubject(commitMessage).matches(AUTOSQUASH_SUBJECT_REGEX)

  fun squash(message1: String, message2: String): String = message1 + REBASE_SQUASH_SEPARATOR + message2

  /**
   * Combines commits messages removing duplicates and trimming autosquash subjects (if target commit is present)
   */
  fun prettySquash(messages: Collection<String>): String {
    val distinctSubjects = messages.mapNotNullTo(mutableSetOf()) { message ->
      if (isAutosquashCommitMessage(message)) null else getSubject(message)
    }

    return messages.mapNotNullTo(mutableSetOf()) { message ->
      if (canAutosquash(message, distinctSubjects)) trimAutosquashSubject(message).takeIf { it.isNotBlank() }
      else message
    }.joinToString(REBASE_SQUASH_SEPARATOR)
  }

  /**
   * Returns true if [commitMessage] has autosquash prefix and target commit subject is present in [commitsSubjects]
   */
  private fun canAutosquash(commitMessage: String, commitsSubjects: Set<String>): Boolean {
    val subject = getSubject(commitMessage)
    val autosquashWith = AUTOSQUASH_SUBJECT_REGEX.find(subject)?.groupValues?.getOrNull(2)
    return autosquashWith in commitsSubjects
  }

  private fun getSubject(message: String): String = message.lineSequence().first()

  private fun removeSubject(commitMessage: String): String = commitMessage.substringAfter("\n", "").trim()
}