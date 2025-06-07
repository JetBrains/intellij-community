// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @Nullable String getPlaceholderValue(LocalTask task, String placeholder) {
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
