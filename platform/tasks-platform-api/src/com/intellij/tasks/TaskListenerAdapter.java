// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks;

import org.jetbrains.annotations.NotNull;

public class TaskListenerAdapter implements TaskListener {
  @Override
  public void taskDeactivated(final @NotNull LocalTask task) {
  }

  @Override
  public void taskActivated(final @NotNull LocalTask task) {
  }

  @Override
  public void taskAdded(final @NotNull LocalTask task) {
  }

  @Override
  public void taskRemoved(final @NotNull LocalTask task) {
  }
}
