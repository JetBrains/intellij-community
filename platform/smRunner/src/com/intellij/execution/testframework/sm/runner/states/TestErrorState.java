// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.states;

import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 */
public class TestErrorState extends TestFailedState {
  public TestErrorState(final @Nullable String localizedMessage,
                        final @Nullable String stackTrace) {
    super(localizedMessage, stackTrace);
  }

  @Override
  public Magnitude getMagnitude() {
    return Magnitude.ERROR_INDEX;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "TEST ERROR";
  }
}
