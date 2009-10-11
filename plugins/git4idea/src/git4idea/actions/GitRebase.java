/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Git rebase action
 */
public class GitRebase extends GitRebaseActionBase {

  /**
   * {@inheritDoc}
   */
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("rebase.action.name");
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  protected GitLineHandler createHandler(Project project, List<VirtualFile> gitRoots, VirtualFile defaultRoot) {
    GitRebaseDialog dialog = new GitRebaseDialog(project, gitRoots, defaultRoot);
    dialog.show();
    if (!dialog.isOK()) {
      return null;
    }
    return dialog.handler();
  }
}
