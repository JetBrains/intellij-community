// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class JUnit5TeamCityRunnerForTestAllSuiteTest {
  @Test
  void testLimitedStacktraceSize() {
    final int limit = 3000; // full length is about 8k locally
    final IOException inner = new IOException("Cause inner");
    final IOException outer = new IOException("Cause outer", inner);
    final Exception head = new Exception("HEAD", outer);
    final String stacktrace = JUnit5TeamCityRunnerForTestAllSuite.TCExecutionListener.getTrace(head, limit);

    assertThat(stacktrace)
      .contains("java.lang.Exception: HEAD")
      .doesNotContain("Caused by: java.io.IOException: Cause outer")
      .contains("Caused by: java.io.IOException: Cause inner")
      .hasSizeLessThan(limit + limit / 10)
    ;
  }

  @Test
  void testLimitedStacktraceSizeWithLongMessage() {
    final int limit = 1000; // full length is about 8k locally
    final IOException inner = new IOException("Cause inner");
    final IOException outer = new IOException("Cause outer", inner);

    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < limit; i++) {
      sb.append(i);
    }
    final Exception head = new Exception("HEAD: " + sb, outer); // exception with a very-very long message
    assertThat(head.getMessage()).hasSizeGreaterThan(limit);

    final String stacktrace = JUnit5TeamCityRunnerForTestAllSuite.TCExecutionListener.getTrace(head, limit);

    assertThat(stacktrace)
      .contains("java.lang.Exception: HEAD")
      .doesNotContain("Caused by: java.io.IOException: Cause outer")
      .contains("Caused by: java.io.IOException: Cause inner")
      .hasSizeLessThan(limit + limit / 10)
    ;
  }
}