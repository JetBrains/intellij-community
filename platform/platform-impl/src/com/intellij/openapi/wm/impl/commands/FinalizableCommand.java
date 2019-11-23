// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.commands;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladimir Kondratyev
 */
public abstract class FinalizableCommand implements Runnable {
  private final Runnable myFinishCallBack;

  protected ToolWindowManager myManager;

  public FinalizableCommand(Runnable finishCallBack) {
    myFinishCallBack = finishCallBack;
  }

  public final void finish() {
    myFinishCallBack.run();
  }

  public void beforeExecute(@NotNull ToolWindowManager toolWindowManager) {
    myManager = toolWindowManager;
  }

  @Nullable
  public Condition<?> getExpireCondition() {
    return null;
  }

  public boolean willChangeState() {
    return true;
  }
}
