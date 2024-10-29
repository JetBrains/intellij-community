// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.CommitPlaceholderProvider;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskRepository;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class DefaultCommitPlaceholderProvider implements CommitPlaceholderProvider {

  @Override
  public String @NotNull [] getPlaceholders(TaskRepository repository) {
    return new String[] { "id", "number", "summary", "project", "taskType"};
  }

  @Nullable
  @Override
  public String getPlaceholderValue(LocalTask task, String placeholder) {
    if ("id".equals(placeholder))
      return task.getPresentableId();
    if ("number".equals(placeholder))
      return task.getNumber();
    if ("summary".equals(placeholder))
      return task.getSummary();
    if ("project".equals(placeholder))
      return StringUtil.notNullize(task.getProject());
    if ("taskType".equals(placeholder))
      return task.getType().name();
    throw new IllegalArgumentException(placeholder);
  }

  @Override
  public String getPlaceholderDescription(String placeholder) {
    return null;
  }
}
