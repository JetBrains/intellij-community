// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class ScheduleForRemovalAction extends AbstractMissingFilesAction {
  @Override
  protected List<VcsException> processFiles(final AbstractVcs vcs, final List<? extends FilePath> files) {
    CheckinEnvironment environment = vcs.getCheckinEnvironment();
    if (environment == null) return Collections.emptyList();
    final List<VcsException> result = environment.scheduleMissingFileForDeletion(files);
    if (result == null) return Collections.emptyList();
    return result;
  }

  @Override
  protected String getName() {
    return null;
  }

  @Override
  protected boolean synchronously() {
    return true;
  }
}