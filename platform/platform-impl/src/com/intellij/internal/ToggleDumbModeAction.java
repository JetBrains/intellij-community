/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class ToggleDumbModeAction extends DumbAwareAction {
  private static final Key<Boolean> DUMB = Key.create("ToggleDumbModeAction");

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    if (isToggledDumb(project)) {
      setToggledDumb(project, false);
    } else {
      setToggledDumb(project, true);
      DumbServiceImpl.getInstance(project).queueTask(new DumbModeTask() {
        @Override
        public void performInDumbMode(@NotNull ProgressIndicator indicator) {
          while (isToggledDumb(project)) {
            indicator.checkCanceled();
            TimeoutUtil.sleep(100);
          }
        }
      });
    }
  }

  private static void setToggledDumb(Project project, boolean value) {
    project.putUserData(DUMB, value);
  }

  private static boolean isToggledDumb(Project project) {
    return project.getUserData(DUMB) == Boolean.TRUE;
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    boolean dumb = DumbServiceImpl.getInstance(project).isDumb();
    if (!dumb && isToggledDumb(project)) {
      setToggledDumb(project, false);
    }
    e.getPresentation().setEnabled(!dumb || isToggledDumb(project));
    e.getPresentation().setText(dumb ? "Exit Dumb Mode" : "Enter Dumb Mode");
  }
}
