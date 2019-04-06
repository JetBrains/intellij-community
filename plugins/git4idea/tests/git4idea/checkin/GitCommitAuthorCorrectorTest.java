// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public void empty_email() {
    assertDoNothing("John Smith <>");
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