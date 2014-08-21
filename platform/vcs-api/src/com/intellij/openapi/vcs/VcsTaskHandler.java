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
import com.intellij.util.containers.MultiMap;

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
        return handler.isEnabled(project);
      }
    });
    return handlers.toArray(new VcsTaskHandler[handlers.size()]);
  }

  public static class TaskInfo {
    // branch name/repository names
    public final MultiMap<String, String> branches;

    public TaskInfo(MultiMap<String, String> branches) {
      this.branches = branches;
    }

    public String getName() {
      return branches.isEmpty() ? null : branches.keySet().iterator().next();
    }

    @Override
    public boolean equals(Object obj) {
      return branches.equals(((TaskInfo)obj).branches);
    }
  }

  private static final ExtensionPointName<VcsTaskHandler> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.vcs.taskHandler");

  public abstract boolean isEnabled(Project project);

  public abstract TaskInfo startNewTask(String taskName);

  public abstract void switchToTask(TaskInfo taskInfo, Runnable invokeAfter);

  public abstract void closeTask(TaskInfo taskInfo, TaskInfo original);

  public abstract TaskInfo getActiveTask();

  public abstract TaskInfo[] getCurrentTasks();
}
