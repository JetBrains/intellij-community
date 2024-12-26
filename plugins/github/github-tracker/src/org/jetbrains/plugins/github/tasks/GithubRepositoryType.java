// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.tasks;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EnumSet;

final class GithubRepositoryType extends BaseRepositoryType<GithubRepository> {
  @Override
  public @NotNull String getName() {
    return "GitHub";
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.Vcs.Vendors.Github;
  }

  @Override
  public @NotNull TaskRepository createRepository() {
    return new GithubRepository(this);
  }

  @Override
  public Class<GithubRepository> getRepositoryClass() {
    return GithubRepository.class;
  }

  @Override
  public @NotNull TaskRepositoryEditor createEditor(GithubRepository repository,
                                                    Project project,
                                                    Consumer<? super GithubRepository> changeListener) {
    return new GithubRepositoryEditor(project, repository, changeListener);
  }

  @Override
  public EnumSet<TaskState> getPossibleTaskStates() {
    return EnumSet.of(TaskState.OPEN, TaskState.RESOLVED);
  }

}
