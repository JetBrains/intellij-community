// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * The main extension point for issue tracking integration.
 *
 * @author Dmitry Avdeev
 */
public abstract class TaskRepositoryType<T extends TaskRepository> implements TaskRepositorySubtype, Comparable<TaskRepositoryType> {
  public static final ExtensionPointName<TaskRepositoryType<?>> EP_NAME = new ExtensionPointName<>("com.intellij.tasks.repositoryType");

  public static @NotNull List<TaskRepositoryType<?>> getRepositoryTypes() {
    return EP_NAME.getExtensionList();
  }

  public static @Unmodifiable @NotNull List<Class<?>> getRepositoryClasses() {
    return ContainerUtil.map(getRepositoryTypes(), TaskRepositoryType::getRepositoryClass);
  }

  public static <T> void addEPListChangeListener(@NotNull Disposable disposable, @NotNull Runnable listener) {
    EP_NAME.addChangeListener(listener, disposable);
  }

  @Override
  public abstract @NotNull String getName();

  @Override
  public abstract @NotNull Icon getIcon();

  public @Nullable @Nls String getAdvertiser() { return null; }

  public abstract @NotNull TaskRepositoryEditor createEditor(T repository, Project project, Consumer<? super T> changeListener);

  public List<TaskRepositorySubtype> getAvailableSubtypes() {
    return Collections.singletonList(this);
  }

  public @NotNull TaskRepository createRepository(TaskRepositorySubtype subtype) {
    return subtype.createRepository();
  }

  @Override
  public abstract @NotNull TaskRepository createRepository();

  public abstract Class<T> getRepositoryClass();

  /**
   * @return states that can be set by {@link TaskRepository#setTaskState(Task, CustomTaskState)}
   * @deprecated Use {@link TaskRepository#getAvailableTaskStates(Task)} instead.
   */
  @Deprecated(forRemoval = true)
  public EnumSet<TaskState> getPossibleTaskStates() {
    return EnumSet.noneOf(TaskState.class);
  }

  public int getSortOrder() {
    return 0;
  }

  @Override
  public int compareTo(@NotNull TaskRepositoryType other) {
    return other.getSortOrder() - this.getSortOrder();
  }
}
