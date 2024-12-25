// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class VcsQuickListContentProviderBase implements VcsQuickListContentProvider {
  @Override
  public @Nullable List<AnAction> getVcsActions(@Nullable Project project, @Nullable AbstractVcs activeVcs, @Nullable AnActionEvent event) {
    if (activeVcs == null || !getVcsName().equals(activeVcs.getName())) return null;

    return collectVcsSpecificActions(ActionManager.getInstance());
  }

  protected abstract @NotNull @NonNls String getVcsName();

  protected abstract List<AnAction> collectVcsSpecificActions(@NotNull ActionManager manager);

  protected static void add(@NotNull @NonNls String actionId, @NotNull ActionManager manager, @NotNull List<? super AnAction> actions) {
    final AnAction action = manager.getAction(actionId);
    assert action != null : "Can not find action " + actionId;
    actions.add(action);
  }
}
