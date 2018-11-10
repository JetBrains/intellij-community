// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider;

import com.intellij.dvcs.actions.DvcsQuickListContentProvider;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;

import java.util.List;

public class HgQuickListProvider extends DvcsQuickListContentProvider {

  private static final Logger LOG = Logger.getInstance(HgQuickListProvider.class.getName());

  @NotNull
  @Override
  protected String getVcsName() {
    return HgVcs.VCS_NAME;
  }

  @Override
  protected void addVcsSpecificActions(@NotNull ActionManager manager, @NotNull List<AnAction> actions) {
    add("hg4idea.branches", manager, actions);
    add("hg4idea.pull", manager, actions);
    add("Vcs.Push", manager, actions);
    add("hg4idea.updateTo", manager, actions);
  }
}
