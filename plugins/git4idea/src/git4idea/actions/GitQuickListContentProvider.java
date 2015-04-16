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

import com.intellij.dvcs.actions.DvcsQuickListContentProvider;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class GitQuickListContentProvider extends DvcsQuickListContentProvider {
  @NotNull
  @Override
  protected String getVcsName() {
    return GitVcs.NAME;
  }

  @Override
  protected void addVcsSpecificActions(@NotNull ActionManager manager, @NotNull List<AnAction> actions) {
    add("Git.Branches", manager, actions);
    add("Vcs.Push", manager, actions);
    add("Git.Stash", manager, actions);
    add("Git.Unstash", manager, actions);

    add("ChangesView.AddUnversioned", manager, actions);
    add("Git.ResolveConflicts", manager, actions);

    // Github
    addSeparator(actions);
    final AnAction githubRebase = manager.getAction("Github.Rebase");
    if (githubRebase != null) {
      actions.add(new Separator(GitBundle.message("vcs.popup.git.github.section")));
      actions.add(githubRebase);
    }
  }

  public List<AnAction> getNotInVcsActions(@Nullable Project project, @Nullable DataContext dataContext) {
    final AnAction action = ActionManager.getInstance().getAction("Git.Init");
    return Collections.singletonList(action);
  }
}
