// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.provider;

import com.intellij.dvcs.actions.DvcsQuickListContentProvider;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;

import java.util.ArrayList;
import java.util.List;

public class HgQuickListProvider extends DvcsQuickListContentProvider {
  @Override
  protected @NotNull String getVcsName() {
    return HgVcs.VCS_NAME;
  }

  @Override
  protected List<AnAction> collectVcsSpecificActions(@NotNull ActionManager manager) {
    List<AnAction> actions = new ArrayList<>();
    add("hg4idea.branches", manager, actions);
    add("hg4idea.pull", manager, actions);
    add("Vcs.Push", manager, actions);
    add("hg4idea.updateTo", manager, actions);
    add("ChangesView.AddUnversioned", manager, actions);
    return actions;
  }
}
