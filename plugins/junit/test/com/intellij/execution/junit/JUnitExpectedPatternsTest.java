/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.junit;

import com.intellij.junit4.ExpectedPatterns;
import com.intellij.rt.execution.junit.ComparisonFailureData;
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
