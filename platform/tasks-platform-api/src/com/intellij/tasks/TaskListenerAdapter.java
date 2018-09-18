package com.intellij.tasks;

import org.jetbrains.annotations.NotNull;

public class TaskListenerAdapter implements TaskListener {
  @Override
  public void taskDeactivated(@NotNull final LocalTask task) {
  }

  @Override
  public void taskActivated(@NotNull final LocalTask task) {
  }

  @Override
  public void taskAdded(@NotNull final LocalTask task) {
  }

  @Override
  public void taskRemoved(@NotNull final LocalTask task) {
  }
}
