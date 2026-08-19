// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker;

import com.intellij.openapi.editor.impl.RangeMarkerStorageImpl;
import com.intellij.openapi.editor.impl.RangeMarkerTest;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

public final class PRangeMarkerTest extends RangeMarkerTest {
  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    RangeMarkerStorageImpl.usePMarkerImplementationIn(()->super.runTestRunnable(testRunnable));
  }
}
