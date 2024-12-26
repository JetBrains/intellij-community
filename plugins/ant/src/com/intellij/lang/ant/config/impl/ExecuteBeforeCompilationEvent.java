// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.ExecutionEvent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

public final class ExecuteBeforeCompilationEvent extends ExecutionEvent {
  public static final @NonNls String TYPE_ID = "beforeCompilation";

  private static final ExecuteBeforeCompilationEvent ourInstance = new ExecuteBeforeCompilationEvent();

  private ExecuteBeforeCompilationEvent() {
  }

  public static ExecuteBeforeCompilationEvent getInstance() {
    return ourInstance;
  }

  @Override
  public @NonNls String getTypeId() {
    return TYPE_ID;
  }

  @Override
  public @Nls String getPresentableName() {
    return AntBundle.message("ant.event.before.compilation.presentable.name");
  }
}

