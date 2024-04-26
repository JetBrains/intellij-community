// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.collaboration.ui.codereview.commits.splitCommitMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class GithubUtilTest {
  @Test
  fun splitCommitMessageToSubjectAndDescriptionInGithubStyle() {
    assertCommitMessage("Subject", "Message", "Subject\n\nMessage")
    assertCommitMessage("Subject", "Message", "Subject\n\r\n\rMessage")
    assertCommitMessage("Subject", "", "Subject")
    assertCommitMessage("Subject", "", "\n\n\nSubject\n\n\n")
    assertCommitMessage("Subject", "Message\n\n\nText", "\n\n\nSubject\n\n\nMessage\n\n\nText")
    assertCommitMessage("Subject", "Message\nText", "Subject\nMessage\nText")
    assertCommitMessage("Subject", "Message Text", "Subject\nMessage Text")
    assertCommitMessage("Subject", "Multiline\nMessage", "Subject\n\nMultiline\nMessage")
    assertCommitMessage("", "", null)
    //No crop for long messages without line separators
    assertCommitMessage(
      "Very long subject string 123 Very long subject string Very long subject string Very long subject string Message",
      "",
      "Very long subject string 123 Very long subject string Very long subject string Very long subject string Message")
    //Yes, we don't crop subject even if subject is long and description is short,
    //we use this behaviour for better consistency and to avoid unelected cases, when you long title is long by design
    //Actually, it's the same behaviour that used in GitHub CLI (https://github.com/github/hub)
    assertCommitMessage(
      "Very long subject string 123 Very long subject string Very long subject string Very long subject string Message",
      "Appendix",
      "Very long subject string 123 Very long subject string Very long subject string Very long subject string Message\nAppendix")
    assertCommitMessage(
      "Very long subject string 123 Very long subject string Very long subject string Very long subject string Message",
      "Appendix",
      "Very long subject string 123 Very long subject string Very long subject string Very long subject string Message\n\nAppendix")

  }

  private fun assertCommitMessage(expectedSubject: String, expectedDescription: String, fullMessage: String?) {
    val message = splitCommitMessage(fullMessage)
    assertEquals(expectedSubject, message.first)
    assertEquals(expectedDescription, message.second)
  }
}
