// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.RollbackProgressModifier;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class RollbackDeletionAction extends AbstractMissingFilesAction {
  @Override
  protected List<VcsException> processFiles(final AbstractVcs vcs, final List<? extends FilePath> files) {
    RollbackEnvironment environment = vcs.getRollbackEnvironment();
    if (environment == null) return Collections.emptyList();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(VcsBundle.message("progress.text.performing.rollback", vcs.getDisplayName()));
    }
    final List<VcsException> result = new ArrayList<>(0);
    try {
      environment.rollbackMissingFileDeletion(files, result, new RollbackProgressModifier(files.size(), indicator));
    }
    catch (ProcessCanceledException e) {
      // for files refresh
    }
    LocalFileSystem.getInstance().refreshIoFiles(ChangesUtil.filePathsToFiles(files));
    return result;
  }

  @Override
  protected String getName() {
    return VcsBundle.message("changes.action.rollback.text");
  }

  @Override
  protected boolean synchronously() {
    return false;
  }
}
