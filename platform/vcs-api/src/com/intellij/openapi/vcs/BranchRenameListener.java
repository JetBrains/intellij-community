// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface BranchRenameListener extends EventListener {

  Topic<BranchRenameListener> VCS_BRANCH_RENAMED = Topic.create("VCS branch renamed", BranchRenameListener.class);

  /**
   * Invoked when a branch has been renamed.
   *
   * @param root    affected VCS root
   * @param oldName name before renaming
   * @param newName name after renaming
   */
  @RequiresEdt
  void branchNameChanged(@NotNull VirtualFile root, @NotNull String oldName, @NotNull String newName);
}
