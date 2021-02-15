// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ImmediateFullValueEvaluator extends XFullValueEvaluator {
  private final String myFullValue;

  public ImmediateFullValueEvaluator(@NotNull String fullValue) {
    myFullValue = fullValue;
  }

  public ImmediateFullValueEvaluator(@NotNull @Nls String linkText, @NotNull String fullValue) {
    super(linkText);
    myFullValue = fullValue;
  }

  @Override
  public void startEvaluation(@NotNull XFullValueEvaluationCallback callback) {
    callback.evaluated(myFullValue);
  }
}
