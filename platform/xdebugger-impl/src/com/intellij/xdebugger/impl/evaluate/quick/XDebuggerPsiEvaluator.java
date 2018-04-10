// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.evaluate.quick;

import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author egor
 */
public interface XDebuggerPsiEvaluator {
  void evaluate(@NotNull PsiElement element, @NotNull XDebuggerEvaluator.XEvaluationCallback callback);
}
