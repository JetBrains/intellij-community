// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.dvcs.actions.DvcsQuickListContentProvider;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

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

    add("Git.ResolveConflicts", manager, actions);
  }
}
