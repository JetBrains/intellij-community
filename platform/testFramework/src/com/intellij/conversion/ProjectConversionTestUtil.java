// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.nio.file.Path;

public final class ProjectConversionTestUtil {
  private ProjectConversionTestUtil() {
  }

  public static void assertNoConversionNeeded(@NotNull Path projectPath) {
    final MyConversionListener listener = new MyConversionListener();
    final ConversionResult result = ConversionService.getInstance().convertSilently(projectPath, listener);
    Assert.assertTrue(result.conversionNotNeeded());
    Assert.assertFalse(listener.isConversionNeeded());
    Assert.assertFalse(listener.isConverted());
  }

  public static void convert(@NotNull Path projectPath) {
    final MyConversionListener listener = new MyConversionListener();
    final ConversionResult result = ConversionService.getInstance().convertSilently(projectPath, listener);
    Assert.assertFalse(result.conversionNotNeeded());
    Assert.assertFalse(result.openingIsCanceled());
    Assert.assertTrue(listener.isConversionNeeded());
    Assert.assertTrue(listener.isConverted());
  }

  public static final class MyConversionListener implements ConversionListener {
    private boolean myConversionNeeded;
    private boolean myConverted;

    @Override
    public void conversionNeeded() {
      myConversionNeeded = true;
    }

    @Override
    public void successfullyConverted(@NotNull Path backupDir) {
      myConverted = true;
    }

    @Override
    public void error(@NotNull String message) {
      Assert.fail(message);
    }

    public boolean isConversionNeeded() {
      return myConversionNeeded;
    }

    public boolean isConverted() {
      return myConverted;
    }
  }
}
