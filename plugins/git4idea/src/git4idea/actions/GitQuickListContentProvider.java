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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.actions.VcsQuickListContentProvider;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Roman.Chernyatchik
 */
public class GitQuickListContentProvider implements VcsQuickListContentProvider {
  public List<AnAction> getVcsActions(@Nullable Project project, @Nullable AbstractVcs activeVcs,
                                      @Nullable DataContext dataContext) {

    if (activeVcs == null || !GitVcs.NAME.equals(activeVcs.getName())) {
      return null;
    }

    final ActionManager manager = ActionManager.getInstance();
    final List<AnAction> actions = new ArrayList<AnAction>();

    actions.add(new Separator(activeVcs.getDisplayName()));
    add("CheckinProject", manager, actions);
    add("CheckinFiles", manager, actions);
    add("ChangesView.Revert", manager, actions);

    addSeparator(actions);
    add("Vcs.ShowTabbedFileHistory", manager, actions);
    add("Annotate", manager, actions);
    add("Compare.SameVersion", manager, actions);

    addSeparator(actions);
    add("Git.Branches", manager, actions);
    add("Git.Push", manager, actions);
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

    return actions;
  }

  public List<AnAction> getNotInVcsActions(@Nullable Project project, @Nullable DataContext dataContext) {
    final AnAction action = ActionManager.getInstance().getAction("Git.Init");
    return Collections.singletonList(action);
  }

  public boolean replaceVcsActionsFor(@NotNull AbstractVcs activeVcs, @Nullable DataContext dataContext) {
    if (!GitVcs.NAME.equals(activeVcs.getName())) {
      return false;
    }
    return true;
  }

  private static void addSeparator(@NotNull final List<AnAction> actions) {
    actions.add(new Separator());
  }

  private static void add(String actionName, ActionManager manager, List<AnAction> actions) {
    final AnAction action = manager.getAction(actionName);
    assert action != null;
    actions.add(action);
  }
}
