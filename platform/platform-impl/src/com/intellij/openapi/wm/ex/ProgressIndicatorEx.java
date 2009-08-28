package com.intellij.openapi.wm.ex;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.util.containers.DoubleArrayList;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

public interface ProgressIndicatorEx extends ProgressIndicator {

  void addStateDelegate(@NotNull ProgressIndicatorEx delegate);

  void initStateFrom(@NotNull ProgressIndicatorEx indicator);

  @NotNull Stack<String> getTextStack();

  @NotNull DoubleArrayList getFractionStack();

  @NotNull Stack<String> getText2Stack();

  int getNonCancelableCount();

  boolean isModalityEntered();

  void finish(@NotNull TaskInfo task);
  
  boolean isFinished(@NotNull TaskInfo task);

  boolean wasStarted();

  void processFinish();
}
