// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.nls;

import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class NlsMessagesTest {
  @Test
  public void testFormatAnd() {
    assertEquals("Java, Kotlin, and Groovy", NlsMessages.formatAndList(Arrays.asList("Java", "Kotlin", "Groovy")));
  }

  @Test
  public void testFormatNarrowAnd() {
    assertEquals("Java, Kotlin, Groovy", NlsMessages.formatNarrowAndList(Arrays.asList("Java", "Kotlin", "Groovy")));
  }

  @Test
  public void testFormatOr() {
    assertEquals("Java, Kotlin, or Groovy", NlsMessages.formatOrList(Arrays.asList("Java", "Kotlin", "Groovy")));
  }
  
  @Test
  public void testJoiningAnd() {
    assertEquals("Java, Kotlin, and Groovy", Stream.of("Java", "Kotlin", "Groovy").collect(NlsMessages.joiningAnd()));
  }

  @Test
  public void testJoiningOr() {
    assertEquals("Java, Kotlin, or Groovy", Stream.of("Java", "Kotlin", "Groovy").collect(NlsMessages.joiningOr()));
  }
  
  @Test
  public void testFormatDuration() {
    assertEquals("0 ms", NlsMessages.formatDuration(0));
    assertEquals("1 ms", NlsMessages.formatDuration(1));
    assertEquals("1 sec", NlsMessages.formatDuration(1000));
    assertEquals("24 days, 20 hr, 31 min, 23 sec, 647 ms", NlsMessages.formatDuration(Integer.MAX_VALUE));
    assertEquals("82 days, 17 hr, 24 min, 43 sec, 647 ms", NlsMessages.formatDuration(Integer.MAX_VALUE+5000000000L));

    assertEquals("1 min, 0 sec, 100 ms", NlsMessages.formatDuration(60100));

    assertEquals("1 sec, 234 ms", NlsMessages.formatDuration(1234));
    assertEquals("12 sec, 345 ms", NlsMessages.formatDuration(12345));
    assertEquals("2 min, 3 sec, 456 ms", NlsMessages.formatDuration(123456));
    assertEquals("20 min, 34 sec, 567 ms", NlsMessages.formatDuration(1234567));
    assertEquals("3 hr, 25 min, 45 sec, 678 ms", NlsMessages.formatDuration(12345678));
    assertEquals("1 day, 10 hr, 17 min, 36 sec, 789 ms", NlsMessages.formatDuration(123456789));
    assertEquals("14 days, 6 hr, 56 min, 7 sec, 890 ms", NlsMessages.formatDuration(1234567890));

    assertEquals("39 days, 2 hr, 30 min, 6 sec, 101 ms", NlsMessages.formatDuration(3378606101L));
  }

  @Test
  public void testFormatDurationApproximate() {
    assertEquals("0 ms", NlsMessages.formatDurationApproximate(0));

    assertEquals("59 sec, 999 ms", NlsMessages.formatDurationApproximate(60000 - 1));
    assertEquals("1 min", NlsMessages.formatDurationApproximate(60000));
    assertEquals("1 min, 0 sec", NlsMessages.formatDurationApproximate(60000 + 1));

    assertEquals("2 min", NlsMessages.formatDurationApproximate(120000 - 1));
    assertEquals("2 min", NlsMessages.formatDurationApproximate(120000));
    assertEquals("2 min, 0 sec", NlsMessages.formatDurationApproximate(120000 + 1));
    assertEquals("2 min, 0 sec", NlsMessages.formatDurationApproximate(120000 + 499));
    assertEquals("2 min, 0 sec", NlsMessages.formatDurationApproximate(120000 + 500));
    assertEquals("2 min, 1 sec", NlsMessages.formatDurationApproximate(120000 + 501));

    assertEquals("2 min, 3 sec", NlsMessages.formatDurationApproximate(123000));
    assertEquals("2 min, 4 sec", NlsMessages.formatDurationApproximate(123789));
    assertEquals("2 min, 3 sec", NlsMessages.formatDurationApproximate(123456));
    assertEquals("1 hr, 1 min", NlsMessages.formatDurationApproximate(3659009));
    assertEquals("2 hr", NlsMessages.formatDurationApproximate(7199000));
    assertEquals("1 day", NlsMessages.formatDurationApproximate((23 * 60 * 60 + 59 * 60 + 59) * 1000L));
    assertEquals("391 days, 1 hr", NlsMessages.formatDurationApproximate(33786061001L));
  }

  @Test
  public void testFormatDurationApproximateNarrow() {
    assertEquals("0\u2009ms", NlsMessages.formatDurationApproximateNarrow(0));

    assertEquals("59\u2009sec 999\u2009ms", NlsMessages.formatDurationApproximateNarrow(60000 - 1));
    assertEquals("1\u2009min", NlsMessages.formatDurationApproximateNarrow(60000));
    assertEquals("1\u2009min 0\u2009sec", NlsMessages.formatDurationApproximateNarrow(60000 + 1));

    assertEquals("2\u2009min", NlsMessages.formatDurationApproximateNarrow(120000 - 1));
    assertEquals("2\u2009min", NlsMessages.formatDurationApproximateNarrow(120000));
    assertEquals("2\u2009min 0\u2009sec", NlsMessages.formatDurationApproximateNarrow(120000 + 1));
    assertEquals("2\u2009min 0\u2009sec", NlsMessages.formatDurationApproximateNarrow(120000 + 499));
    assertEquals("2\u2009min 0\u2009sec", NlsMessages.formatDurationApproximateNarrow(120000 + 500));
    assertEquals("2\u2009min 1\u2009sec", NlsMessages.formatDurationApproximateNarrow(120000 + 501));

    assertEquals("2\u2009min 3\u2009sec", NlsMessages.formatDurationApproximateNarrow(123000));
    assertEquals("2\u2009min 4\u2009sec", NlsMessages.formatDurationApproximateNarrow(123789));
    assertEquals("2\u2009min 3\u2009sec", NlsMessages.formatDurationApproximateNarrow(123456));
    assertEquals("1\u2009hr 1\u2009min", NlsMessages.formatDurationApproximateNarrow(3659009));
    assertEquals("2\u2009hr", NlsMessages.formatDurationApproximateNarrow(7199000));
    assertEquals("1\u2009day", NlsMessages.formatDurationApproximateNarrow((23 * 60 * 60 + 59 * 60 + 59) * 1000L));
    assertEquals("391\u2009days 1\u2009hr", NlsMessages.formatDurationApproximateNarrow(33786061001L));
  }

  @Test
  public void testFormatDurationPadded() {
    assertEquals("0ms", NlsMessages.formatDurationPadded(0));
    assertEquals("1s 000ms", NlsMessages.formatDurationPadded(1000));
    assertEquals("1s 001ms", NlsMessages.formatDurationPadded(1001));
    assertEquals("2m 00s 000ms", NlsMessages.formatDurationPadded(TimeUnit.MINUTES.toMillis(2)));
    assertEquals("2h 00m 00s 000ms", NlsMessages.formatDurationPadded(TimeUnit.HOURS.toMillis(2)));
    assertEquals("2d 00h 00m 00s 000ms", NlsMessages.formatDurationPadded(TimeUnit.DAYS.toMillis(2)));
    assertEquals("1,434,852d 16h 13m 50s 987ms", NlsMessages.formatDurationPadded(123971271230987L));
  }
}
