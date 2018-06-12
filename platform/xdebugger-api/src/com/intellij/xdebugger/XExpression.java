// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger;

import com.intellij.lang.Language;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author egor
*/
public interface XExpression {
  @NotNull String getExpression();
  @Nullable Language getLanguage();
  @Nullable String getCustomInfo();
  @NotNull EvaluationMode getMode();
}
