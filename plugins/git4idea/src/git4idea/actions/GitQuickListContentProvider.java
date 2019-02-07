// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.dvcs.actions.DvcsQuickListContentProvider;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GitQuickListContentProvider extends DvcsQuickListContentProvider {
  @NotNull
  @Override
  protected String getVcsName() {
    return GitVcs.NAME;
  }

  @Override
  protected List<AnAction> collectVcsSpecificActions(@NotNull ActionManager manager) {
    List<AnAction> actions = new ArrayList<>();
    add("Git.Branches", manager, actions);
    add("Vcs.Push", manager, actions);
    add("Git.Stash", manager, actions);
    add("Git.Unstash", manager, actions);

    add("ChangesView.AddUnversioned", manager, actions);
    add("Git.ResolveConflicts", manager, actions);
    return actions;
  }
}
