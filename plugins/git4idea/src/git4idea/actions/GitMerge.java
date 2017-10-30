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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitMergeDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class GitMerge extends GitMergeAction {

  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("merge.action.name");
  }

  @Nullable
  @Override
  protected DialogState displayDialog(@NotNull Project project, @NotNull List<VirtualFile> gitRoots, @NotNull VirtualFile defaultRoot) {
    final GitMergeDialog dialog = new GitMergeDialog(project, gitRoots, defaultRoot);
    try {
      dialog.updateBranches();
    }
    catch (VcsException e) {
      GitVcs vcs = GitVcs.getInstance(project);
      if (vcs.getExecutableValidator().checkExecutableAndShowMessageIfNeeded(null)) {
        vcs.showErrors(Collections.singletonList(e), GitBundle.getString("merge.retrieving.branches"));
      }
      return null;
    }
    if (!dialog.showAndGet()) {
      return null;
    }
    return new DialogState(dialog.getSelectedRoot(), GitBundle.message("merging.title", dialog.getSelectedRoot().getPath()),
                           () -> dialog.handler());
  }
}
