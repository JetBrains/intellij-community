/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.actions.AbstractCommitChangesAction;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ShelveChangesAction extends AbstractCommitChangesAction {
  protected String getActionName(VcsContext dataContext) {
    return VcsBundle.message("shelve.changes.action");
  }

  @Override @Nullable
  protected CommitExecutor getExecutor(Project project) {
    return new ShelveChangesCommitExecutor(project);
  }
}