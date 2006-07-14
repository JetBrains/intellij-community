package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.ExecutionEvent;
import org.jetbrains.annotations.NonNls;

public final class ExecuteAfterCompilationEvent extends ExecutionEvent {
  @NonNls public static final String TYPE_ID = "afterCompilation";

  private static final ExecuteAfterCompilationEvent ourInstance = new ExecuteAfterCompilationEvent();

  private ExecuteAfterCompilationEvent() {
  }

  public static ExecuteAfterCompilationEvent getInstance() {
    return ourInstance;
  }

  public String getTypeId() {
    return TYPE_ID;
  }

  public String getPresentableName() {
    return AntBundle.message("ant.event.after.compilation.presentable.name");
  }
}
