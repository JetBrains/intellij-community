/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.checkin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GitCommitAuthorCorrectorTest {

  @Test
  public void add_space_before_email_in_brackets() {
    assertCorrection("foo<foo@email.com>", "foo <foo@email.com>");
  }

  @Test
  public void add_brackets() {
    String expected = "John Smith <john.smith@email.com>";
    assertCorrection("John Smith john.smith@email.com", expected);
    assertCorrection("John Smith <john.smith@email.com", expected);
    assertCorrection("John Smith john.smith@email.com>", expected);
  }

  @Test
  public void no_correction_needed() {
    assertDoNothing("John Smith <john.smith@email.com>");
  }

  @Test
  public void correction_not_possible() {
    assertDoNothing("foo");
    assertDoNothing("foo bar");
  }

  private static void assertCorrection(String source, String expected) {
    assertEquals(expected, GitCommitAuthorCorrector.correct(source));
  }

  private static void assertDoNothing(String source) {
    assertCorrection(source, source);
  }

}