/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public class BackgroundableActionLock {
  @NotNull private final Project myProject;
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


  @NotNull
  public static BackgroundableActionLock getLock(@NotNull Project project, Object @NotNull ... keys) {
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

  @NotNull
  private static ProjectLevelVcsManagerImpl getManager(@NotNull Project project) {
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
