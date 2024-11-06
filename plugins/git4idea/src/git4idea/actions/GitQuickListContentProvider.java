// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions;

import com.intellij.dvcs.actions.DvcsQuickListContentProvider;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GitQuickListContentProvider extends DvcsQuickListContentProvider {
  @Override
  protected @NotNull String getVcsName() {
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

    add("Git.Unshallow", manager, actions);
    return actions;
  }

  @Override
  protected void customizeActions(@NotNull ActionManager manager, @NotNull List<? super AnAction> actions) {
    String commitStageActionName = "Git.Commit.Stage";
    String stageAllActionName = "Git.Stage.Add.Tracked";
    addAfter(commitStageActionName, IdeActions.ACTION_CHECKIN_PROJECT, manager, actions);
    addAfter(stageAllActionName, commitStageActionName, manager, actions);
    super.customizeActions(manager, actions);
  }

  protected static void addAfter(String actionName, String anchorActionName, ActionManager manager, List<? super AnAction> actions) {
    AnAction action = manager.getAction(actionName);
    assert action != null : "Can not find action " + actionName;

    AnAction anchorAction = manager.getAction(anchorActionName);
    assert anchorAction != null : "Can not find action " + anchorActionName;

    int index = actions.indexOf(anchorAction);
    actions.add(index >= 0 ? index + 1 : actions.size(), action);
  }
}
