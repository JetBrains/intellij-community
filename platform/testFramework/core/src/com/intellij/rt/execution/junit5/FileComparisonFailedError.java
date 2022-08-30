// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.execution.junit5;

import com.intellij.rt.execution.junit.FileComparisonData;
import org.jetbrains.annotations.Nullable;
import org.opentest4j.AssertionFailedError;

public class FileComparisonFailedError extends AssertionFailedError implements FileComparisonData {
  private final String myExpectedPath;
  private final String myActualPath;

  public FileComparisonFailedError(String message, Object expected, Object actual, @Nullable String expectedPath, @Nullable String actualPath) {
    super(message, expected, actual);
    myExpectedPath = expectedPath;
    myActualPath = actualPath;
  }

  @Override
  public String getFilePath() {
    return myExpectedPath;
  }

  @Override
  public String getActualFilePath() {
    return myActualPath;
  }

  @Override
  public String getActualStringPresentation() {
    return isActualDefined() ? getActual().getStringRepresentation() : null;
  }

  @Override
  public String getExpectedStringPresentation() {
    return isExpectedDefined() ? getExpected().getStringRepresentation() : null;
  }
}