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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * This class is programmatically instantiated and registered when opening and closing projects
 * and thus not registered in plugin.xml
 */
@SuppressWarnings({"ComponentNotRegistered"})
public class ProjectWindowAction extends ToggleAction implements DumbAware {

  private ProjectWindowAction previous;
  private ProjectWindowAction next;

  public ProjectWindowAction(@NotNull String projectName, ProjectWindowAction previous) {
    super(projectName);
    if (previous != null) {
      this.previous = previous;
      this.next = previous.next;
      previous.next = this;
      if (previous.previous == previous) {
        previous.previous = this;
      }
    } else {
      this.previous = this;
      this.next = this;
    }
  }

  public void dispose() {
    if (previous == this) {
      assert next == this;
      return;
    }
    if (next == this) {
      assert false;
      return;
    }
    previous.next = next;
    next.previous = previous;
  }

  public ProjectWindowAction getPrevious() {
    return previous;
  }

  public ProjectWindowAction getNext() {
    return next;
  }

  @Nullable
  public static Frame findProjectFrame(@NotNull String projectName) {
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      if (projectName.equals(project.getName())) {
        final WindowManager windowManager = WindowManager.getInstance();
        return windowManager.getFrame(project);
      }
    }
    return null;
  }

  public boolean isSelected(AnActionEvent e) {
    // show check mark for active and visible project frame
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      return false;
    }
    final String text = getTemplatePresentation().getText();
    return text.equals(project.getName());
  }

  public void setSelected(@Nullable AnActionEvent e, boolean selected) {
    if (!selected) {
      return;
    }
    final Frame projectFrame = findProjectFrame(getTemplatePresentation().getText());
    if (projectFrame == null) {
      return;
    }
    final int frameState = projectFrame.getExtendedState();
    if ((frameState & Frame.ICONIFIED) == Frame.ICONIFIED) {
      // restore the frame if it is minimized
      projectFrame.setExtendedState(frameState ^ Frame.ICONIFIED);
    }
    // bring the frame forward
    projectFrame.toFront();
  }
}