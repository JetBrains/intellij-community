// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.awt.*;

public abstract class QuickEvaluateHandler {

  public abstract boolean isEnabled(@NotNull Project project);

  public boolean isEnabled(@NotNull Project project, @NotNull AnActionEvent event) {
    return isEnabled(project);
  }

  @Nullable
  public abstract AbstractValueHint createValueHint(@NotNull Project project, @NotNull Editor editor, @NotNull Point point, ValueHintType type);

  @NotNull
  public CancellableHint createValueHintAsync(@NotNull Project project, @NotNull Editor editor, @NotNull Point point, ValueHintType type) {
    return CancellableHint.resolved(createValueHint(project, editor, point, type));
  }

  public abstract boolean canShowHint(@NotNull Project project);

  public abstract int getValueLookupDelay(final Project project);

  public record CancellableHint(@NotNull Promise<AbstractValueHint> hintPromise, @Nullable Promise<ExpressionInfo> infoPromise) {
    public static CancellableHint resolved(@Nullable AbstractValueHint hint)  {
      return new CancellableHint(Promises.resolvedPromise(hint), null);
    }

    public void tryCancel() {
      if (infoPromise instanceof CancellablePromise<ExpressionInfo>) {
        ((CancellablePromise<ExpressionInfo>)infoPromise).cancel();
      }
    }
  }
}
