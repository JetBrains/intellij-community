// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.junit4.ExpectedPatterns;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.execution.junit.ComparisonFailureData;
import com.intellij.rt.execution.testFrameworks.AbstractExpectedPatterns;
import org.junit.Assert;
import org.junit.ComparisonFailure;
import org.junit.Test;

public class JUnitExpectedPatternsTest {
  @Test
  public void testArrayComparison() {
    Assert.assertNotNull(createNotification("arrays first differed at element [0]; expected: java.lang.String<Text> but was: java.lang.StringBuilder<Text>"));
  }

  @Test
  public void testWithTailNotMatch() {
    Assert.assertNull(createNotification("line1 expected:<java.lang.String<Text>> but was:<java.lang.StringBuilder<Text>> and here some very important tail"));
  }

  @Test
  public void testMultiplePatternsInOneAssertion() {
    Assert.assertNull(createNotification("line1 expected:<java.lang.String<Text>> but was:<java.lang.StringBuilder<Text>>\n" + 
                                         "line2 expected:<java.lang.String<Text1>> but was:<java.lang.StringBuilder<Text1>>"));
  }

  @Test
  public void testHamcrestAssertThatWithReason() {
    Assert.assertNotNull(createNotification("""
                                              reason
                                              Expected: is "aaa\\naa"
                                                   but:    was "bbb\\nbb\""""));
  }

  @Test
  public void testMessageTooLongDefaultThreshold() {
    String longMessage = StringUtil.repeat("a", AbstractExpectedPatterns.DEFAULT_MESSAGE_LENGTH_THRESHOLD);
    Assert.assertNull(createNotification("""
                                           reason
                                           Expected: is "aaa\\naa"
                                                but:    was \"""" + longMessage + "\""));
  }

  @Test
  public void testMessageTooLongOverrideThreshold() {
    try {
      System.setProperty(
        AbstractExpectedPatterns.MESSAGE_LENGTH_THRESHOLD_PROPERTY,
        Integer.toString(AbstractExpectedPatterns.DEFAULT_MESSAGE_LENGTH_THRESHOLD * 2)
      );
      String shortEnoughMessage = StringUtil.repeat("a", AbstractExpectedPatterns.DEFAULT_MESSAGE_LENGTH_THRESHOLD);
      Assert.assertNotNull(createNotification("""
                                                reason
                                                Expected: is "aaa\\naa"
                                                     but:    was \"""" + shortEnoughMessage + "\""));
      String longMessage = StringUtil.repeat("a", AbstractExpectedPatterns.DEFAULT_MESSAGE_LENGTH_THRESHOLD * 2);
      Assert.assertNull(createNotification("""
                                             reason
                                             Expected: is "aaa\\naa"
                                                  but:    was \"""" + longMessage + "\""));
    }
    finally {
      System.clearProperty(AbstractExpectedPatterns.MESSAGE_LENGTH_THRESHOLD_PROPERTY);
    }
  }

  @Test
  public void testHamcrestAssertThatEqWithReason() {
    Assert.assertNotNull(createNotification("""
                                              reason
                                              Expected: "aaa\\naa"
                                                   got: "bbb\\nbb\""""));
  }

  @Test
  public void testHamcrestAssertThatEqWithReasonTrim() {
    ComparisonFailureData notification = createNotification("""

                                                              Expected: is <2>
                                                                   but: was <1>""");
    Assert.assertNotNull(notification);
    Assert.assertEquals("is <2>", notification.getExpected());
  }

  @Test
  public void testNonGreedyGt() {
    ComparisonFailureData notification = createNotification("expected:<<foo>> but was:<hi with <brackets>>");
    Assert.assertNotNull(notification);
    Assert.assertEquals("<foo>", notification.getExpected());
    Assert.assertEquals("hi with <brackets>", notification.getActual());
  }

  @Test
  public void testJunitGuava() {
    ComparisonFailureData notification = createNotification("expected: com.google.common.collect.SingletonImmutableList<[1]> but was: com.google.common.collect.SingletonImmutableList<[1]>");
    Assert.assertNotNull(notification);
    Assert.assertEquals("com.google.common.collect.SingletonImmutableList<[1]>", notification.getExpected());
    Assert.assertEquals("com.google.common.collect.SingletonImmutableList<[1]>", notification.getActual());
  }

  @Test
  public void testCustomComparisonException() {
    ComparisonFailureData notification = createNotification(
      new CustomComparisonFailure("message", "expected", "actual")
    );
    Assert.assertNotNull(notification);
    Assert.assertEquals("expected", notification.getExpected());
    Assert.assertEquals("actual", notification.getActual());
  }

  private static ComparisonFailureData createNotification(String message) {
    return ExpectedPatterns.createExceptionNotification(new Throwable(message));
  }

  private static class CustomComparisonFailure extends ComparisonFailure {
    CustomComparisonFailure(String message, String expected, String actual) {
      super(message, expected, actual);
    }
  }

  private static ComparisonFailureData createNotification(Throwable throwable) {
    return ExpectedPatterns.createExceptionNotification(throwable);
  }
}
