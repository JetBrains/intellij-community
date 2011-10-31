/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ProjectWindowActionGroup extends DefaultActionGroup {

  private ProjectWindowAction latest = null;

  public void addProject(@NotNull String name) {
    final ProjectWindowAction windowAction = new ProjectWindowAction(name, latest);
    add(windowAction);
    latest = windowAction;
  }

  public void removeProject(@NotNull String name) {
    final ProjectWindowAction windowAction = findWindowAction(name);
    if (windowAction == null) {
      return;
    }
    if (latest == windowAction) {
      final ProjectWindowAction previous = latest.getPrevious();
      if (previous != latest) {
        latest = previous;
      } else {
        latest = null;
      }
    }
    remove(windowAction);
    windowAction.dispose();
  }

  public boolean isEnabled() {
    return latest != null && latest.getPrevious() != latest;
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  public void activateNextWindow(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    final ProjectWindowAction windowAction = findWindowAction(project.getName());
    if (windowAction == null) {
      return;
    }
    final ProjectWindowAction next = windowAction.getNext();
    if (next != null) {
      next.setSelected(e, true);
    }
  }

  public void activatePreviousWindow(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    final ProjectWindowAction windowAction = findWindowAction(project.getName());
    if (windowAction == null) {
      return;
    }
    final ProjectWindowAction previous = windowAction.getPrevious();
    if (previous != null) {
      previous.setSelected(e, true);
    }
  }

  private ProjectWindowAction findWindowAction(String name) {
    final AnAction[] children = getChildren(null);
    for (AnAction child : children) {
      if (!(child instanceof ProjectWindowAction)) {
        continue;
      }
      final ProjectWindowAction windowAction = (ProjectWindowAction) child;
      if (name.equals(windowAction.getTemplatePresentation().getText())) {
        return windowAction;
      }
    }
    return null;
  }
}