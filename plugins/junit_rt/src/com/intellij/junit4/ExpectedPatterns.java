// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit4;

import com.intellij.rt.execution.junit.ComparisonFailureData;
import com.intellij.rt.execution.testFrameworks.AbstractExpectedPatterns;

public final class ExpectedPatterns extends AbstractExpectedPatterns {
  public static ComparisonFailureData createExceptionNotification(String message) {
    return parseComparisonFailure(message);
  }

  public static ComparisonFailureData createExceptionNotification(Throwable assertion) {
    if (isComparisonFailure(assertion)) {
      return ComparisonFailureData.create(assertion);
    }
    try {
      final Throwable cause = assertion.getCause();
      if (isComparisonFailure(cause)) {
        return ComparisonFailureData.create(cause);
      }
    }
    catch (Throwable ignore) {
    }

    final String message = assertion.getMessage();
    if (message != null) {
      return createExceptionNotification(message);
    }
    return null;
  }

  private static boolean isComparisonFailure(Throwable throwable) {
    if (throwable == null) return false;
    return ComparisonFailureData.isComparisonFailure(throwable.getClass());
  }
}
