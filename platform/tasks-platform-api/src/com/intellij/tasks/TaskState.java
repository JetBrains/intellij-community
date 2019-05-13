/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  public String getPresentableName() {
    return myPresentableName;
  }
}
