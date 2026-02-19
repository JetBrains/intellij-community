// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.ExecutionEvent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

public final class ExecuteAfterCompilationEvent extends ExecutionEvent {
  public static final @NonNls String TYPE_ID = "afterCompilation";

  private static final ExecuteAfterCompilationEvent ourInstance = new ExecuteAfterCompilationEvent();

  private ExecuteAfterCompilationEvent() {
  }

  public static ExecuteAfterCompilationEvent getInstance() {
    return ourInstance;
  }

  @Override
  public @NonNls String getTypeId() {
    return TYPE_ID;
  }

  @Override
  public @Nls String getPresentableName() {
    return AntBundle.message("ant.event.after.compilation.presentable.name");
  }
}
