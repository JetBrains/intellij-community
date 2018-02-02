/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.i18n.GitBundle;
import git4idea.tag.GitTagDialogHandler;
import git4idea.ui.GitTagDialog;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Git "tag" action
 */
public class GitTag extends GitRepositoryAction {
  private static ExtensionPointName<GitTagDialogHandler> handlerPointName = ExtensionPointName.create("Git4Idea.GitTagDialogHandler");
  private static List<GitTagDialogHandler> handlerList = Arrays.asList(handlerPointName.getExtensions());

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("tag.action.name");
  }

  /**
   * {@inheritDoc}
   */
  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot) {
    GitTagDialog d = new GitTagDialog(project, gitRoots, defaultRoot);
    handlerList.forEach(handler -> handler.createDialog(d));
    if (d.showAndGet()) {
      new Task.Modal(project, "Tagging...", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          handlerList.forEach(GitTagDialogHandler::beforeCheckin);
          for (GitTagDialogHandler handler : handlerList) {
            if (handler.beforeCheckin() == GitTagDialogHandler.ReturnResult.CANCEL) return;
          }
          d.runAction();
        }
      }.queue();
    }
  }
}
