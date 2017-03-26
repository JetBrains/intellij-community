/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github;

import com.intellij.openapi.util.Couple;
import org.junit.Test;

import static org.jetbrains.plugins.github.util.GithubUtil.getGithubLikeFormattedDescriptionMessage;
import static org.junit.Assert.assertEquals;

public class GithubUtilTest {
  @Test
  public void splitCommitMessageToSubjectAndDescriptionInGithubStyle() {
    assertCommitMessage("Subject", "Message", "Subject\n\nMessage");
    assertCommitMessage("Subject", "Message", "Subject\n\r\n\rMessage");
    assertCommitMessage("Subject", "", "Subject");
    assertCommitMessage("Subject", "", "\n\n\nSubject\n\n\n");
    assertCommitMessage("Subject", "Message\n\n\nText", "\n\n\nSubject\n\n\nMessage\n\n\nText");
    assertCommitMessage("Subject", "Message\nText", "Subject\nMessage\nText");
    assertCommitMessage("Subject", "Message Text", "Subject\nMessage Text");
    assertCommitMessage("Subject", "Multiline\nMessage", "Subject\n\nMultiline\nMessage");
    assertCommitMessage("", "", null);
    //No crop for long messages without line separators
    assertCommitMessage(
      "Very long subject string 123 Very long subject string Very long subject string Very long subject string Message",
      "",
      "Very long subject string 123 Very long subject string Very long subject string Very long subject string Message");
    //Yes, we don't crop subject even if subject is long and description is short,
    //we use this behaviour for better consistency and to avoid unelected cases, when you long title is long by design
    //Actually, it's the same behaviour that used in GitHub CLI (https://github.com/github/hub)
    assertCommitMessage(
      "Very long subject string 123 Very long subject string Very long subject string Very long subject string Message",
      "Appendix",
      "Very long subject string 123 Very long subject string Very long subject string Very long subject string Message\nAppendix");
    assertCommitMessage(
      "Very long subject string 123 Very long subject string Very long subject string Very long subject string Message",
      "Appendix",
      "Very long subject string 123 Very long subject string Very long subject string Very long subject string Message\n\nAppendix");

  }

  private static void assertCommitMessage(String expectedSubject, String expectedDescription, String fullMessage) {
    Couple<String> message = getGithubLikeFormattedDescriptionMessage(fullMessage);
    assertEquals(expectedSubject, message.first);
    assertEquals(expectedDescription, message.second);
  }
}
