// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks;

import org.jetbrains.annotations.NotNull;

/**
 * Predefined common task states were used before {@link CustomTaskState} was introduced.
 *
 * @author Dmitry Avdeev
 */
public enum TaskState {
  SUBMITTED("Submitted"),
  OPEN("Open"),
  IN_PROGRESS("In Progress"),
  REOPENED("Reopened"),
  RESOLVED("Resolved"),

  OTHER("Other");

  private final String myPresentableName;

  TaskState(@NotNull String presentableName) {
    myPresentableName = presentableName;
  }

  public @NotNull String getPresentableName() {
    return myPresentableName;
  }
}
