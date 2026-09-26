// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.impl.evaluate.XEvaluationOrigin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface XEvaluationCallbackWithOrigin {
  XEvaluationOrigin getOrigin();

  @ApiStatus.Internal
  static @NotNull XEvaluationOrigin getOrigin(@NotNull XDebuggerEvaluator.XEvaluationCallback callback) {
    return callback instanceof XEvaluationCallbackWithOrigin callbackWithOrigin ?
           callbackWithOrigin.getOrigin() : XEvaluationOrigin.UNSPECIFIED;
  }
}
