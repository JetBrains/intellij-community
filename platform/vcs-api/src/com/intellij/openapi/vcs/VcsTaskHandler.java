/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 16.07.13
 */
public abstract class VcsTaskHandler {

  public static VcsTaskHandler[] getAllHandlers(final Project project) {
    VcsTaskHandler[] extensions = EXTENSION_POINT_NAME.getExtensions(project);
    List<VcsTaskHandler> handlers = ContainerUtil.filter(extensions, new Condition<VcsTaskHandler>() {
      @Override
      public boolean value(VcsTaskHandler handler) {
        return handler.isEnabled();
      }
    });
    return handlers.toArray(new VcsTaskHandler[handlers.size()]);
  }

  public static class TaskInfo {

    private final String myBranch;
    private final Collection<String> myRepositories;

    public TaskInfo(String branch, Collection<String> repositories) {
      myBranch = branch;
      myRepositories = repositories;
    }

    public String getName() {
      return myBranch;
    }

    public Collection<String> getRepositories() {
      return myRepositories;
    }

    @Override
    public String toString() {
      return getName();
    }
  }

  private static final ExtensionPointName<VcsTaskHandler> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.vcs.taskHandler");

  public abstract boolean isEnabled();

  public abstract TaskInfo startNewTask(@NotNull String taskName);

  public abstract void switchToTask(TaskInfo taskInfo, Runnable invokeAfter);

  public abstract void closeTask(@NotNull TaskInfo taskInfo, @NotNull TaskInfo original);

  public abstract boolean isSyncEnabled();

  /**
   * @return currently active (checked out) tasks (branches)
   */
  public abstract TaskInfo[] getCurrentTasks();

  /**
   * @return all existing tasks (branches)
   */
  public abstract TaskInfo[] getAllExistingTasks();
}
