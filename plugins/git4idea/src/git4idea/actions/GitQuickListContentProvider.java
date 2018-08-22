// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public List<AnAction> getNotInVcsActions(@Nullable Project project, @Nullable DataContext dataContext) {
    final AnAction action = ActionManager.getInstance().getAction("Git.Init");
    return Collections.singletonList(action);
  }
}
