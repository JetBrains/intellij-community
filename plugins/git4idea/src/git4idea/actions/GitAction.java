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
package git4idea.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Common class for most git actions.
 * @author Kirill Likhodedov
 */
public abstract class GitAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || project.isDisposed()) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    presentation.setEnabled(isEnabled(e));
  }

  /**
   * Checks if this action should be enabled.
   * Called in {@link #update(com.intellij.openapi.actionSystem.AnActionEvent)}, so don't execute long tasks here.
   * @return true if the action is enabled.
   */
  protected boolean isEnabled(@NotNull AnActionEvent event) {
    return true;
  }

}
