// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.util.ExceptionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveredThrowableTest {
  @Test void noStacktrace() {
    doTest("java.lang.IllegalArgumentException: test");
  }

  @Test void someStacktrace() {
    doTest(
      """
        java.lang.IllegalArgumentException: test
        \tat com.intellij.diagnostic.RecoveredThrowableTest.recovering(RecoveredThrowableTest.java:17)
        \tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        \tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
        \tat java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        \tat java.base/java.lang.reflect.Method.invoke(Method.java:568)"""
    );
  }

  @Test void ignoring() {

  }

  private static void doTest(String text) {
    var recovered = RecoveredThrowable.fromString(text);
    assertThat(ExceptionUtil.getThrowableText(recovered)).isEqualTo(text);
  }
}
