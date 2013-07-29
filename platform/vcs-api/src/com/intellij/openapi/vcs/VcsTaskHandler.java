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
import com.intellij.util.containers.MultiMap;

/**
 * @author Dmitry Avdeev
 *         Date: 16.07.13
 */
public abstract class VcsTaskHandler {

  public static VcsTaskHandler[] getAllHandlers(Project project) {
    return EXTENSION_POINT_NAME.getExtensions(project);
  }

  public static class TaskInfo {
    // branch name/repository names
    public final MultiMap<String, String> branches;

    public TaskInfo(MultiMap<String, String> branches) {
      this.branches = branches;
    }
  }

  private static final ExtensionPointName<VcsTaskHandler> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.vcs.taskHandler");

  public abstract TaskInfo startNewTask(String taskName);

  public abstract void switchToTask(TaskInfo taskInfo);

  public abstract void closeTask(TaskInfo taskInfo, TaskInfo original);

  public abstract TaskInfo getActiveTask();
}
