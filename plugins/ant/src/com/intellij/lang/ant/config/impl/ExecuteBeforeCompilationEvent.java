package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.ExecutionEvent;
import com.intellij.lang.ant.resources.AntBundle;
import org.jetbrains.annotations.NonNls;

public final class ExecuteBeforeCompilationEvent extends ExecutionEvent {
  @NonNls public static final String TYPE_ID = "beforeCompilation";

  private static final ExecuteBeforeCompilationEvent ourInstance = new ExecuteBeforeCompilationEvent();

  private ExecuteBeforeCompilationEvent() {
  }

  public static ExecuteBeforeCompilationEvent getInstance() {
    return ourInstance;
  }

  public String getTypeId() {
    return TYPE_ID;
  }

  public String getPresentableName() {
    return AntBundle.message("ant.event.before.compilation.presentable.name");
  }
}

