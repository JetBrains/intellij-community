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
package com.intellij.execution.testframework.autotest;

import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dennis.Ushakov
 */
public class AdjustAutotestDelayActionGroup extends ActionGroup {
  public static final int MAX_DELAY = 10;
  private final Project myProject;
  private final DataContext myDataContext;

  public AdjustAutotestDelayActionGroup(@NotNull Project project, @NotNull JComponent parent) {
    super("Set AutoTest Delay", true);
    myProject = project;
    myDataContext = DataManager.getInstance().getDataContext(parent);
  }

  @Override
  public void update(AnActionEvent e) {
    RunContentDescriptor descriptor = LangDataKeys.RUN_CONTENT_DESCRIPTOR.getData(myDataContext);
    boolean visible = false;
    if (descriptor != null) {
      for (AnAction action : descriptor.getRestartActions()) {
        if (action instanceof ToggleAutoTestAction) {
          visible = true;
          break;
        }
      }
    }
    e.getPresentation().setVisible(visible);
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    final AnAction[] actions = new AnAction[MAX_DELAY];
    for (int i = 0; i < MAX_DELAY; i++) {
      actions[i] = new SetAutoTestDelayAction(myProject, i + 1);
    }
    return actions;
  }

  private static class SetAutoTestDelayAction extends ToggleAction {
    private final int myDelay;
    @NotNull private final Project myProject;

    public SetAutoTestDelayAction(@NotNull Project project, int delay) {
      super(delay + "s");
      myProject = project;
      myDelay = delay * 1000;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return AutoTestManager.getInstance(myProject).getDelay() == myDelay;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      AutoTestManager.getInstance(myProject).setDelay(myDelay);
    }
  }
}
