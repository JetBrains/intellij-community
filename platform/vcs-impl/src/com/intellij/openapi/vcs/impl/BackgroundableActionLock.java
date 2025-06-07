// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public class BackgroundableActionLock {
  private final @NotNull Project myProject;
  private final Object @NotNull [] myKeys;

  BackgroundableActionLock(@NotNull Project project, final Object @NotNull [] keys) {
    myProject = project;
    myKeys = keys;
  }

  @CalledInAny
  public boolean isLocked() {
    return isLocked(myProject, myKeys);
  }

  @RequiresEdt
  public void lock() {
    lock(myProject, myKeys);
  }

  @RequiresEdt
  public void unlock() {
    unlock(myProject, myKeys);
  }


  public static @NotNull BackgroundableActionLock getLock(@NotNull Project project, Object @NonNls @NotNull ... keys) {
    return new BackgroundableActionLock(project, keys);
  }

  @CalledInAny
  public static boolean isLocked(@NotNull Project project, Object @NotNull ... keys) {
    return getManager(project).isBackgroundTaskRunning(keys);
  }

  @RequiresEdt
  public static void lock(@NotNull Project project, Object @NotNull ... keys) {
    getManager(project).startBackgroundTask(keys);
  }

  @RequiresEdt
  public static void unlock(@NotNull Project project, Object @NotNull ... keys) {
    if (project.isDisposed()) return;
    getManager(project).stopBackgroundTask(keys);
  }

  private static @NotNull ProjectLevelVcsManagerImpl getManager(@NotNull Project project) {
    return ProjectLevelVcsManagerImpl.getInstanceImpl(project);
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BackgroundableActionLock lock = (BackgroundableActionLock)o;
    return myProject.equals(lock.myProject) &&
           Arrays.equals(myKeys, lock.myKeys);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(myProject);
    result = 31 * result + Arrays.hashCode(myKeys);
    return result;
  }
}
