// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.fileTypes.FileTypesBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
final class ChangelistConflictAccessProvider extends WritingAccessProvider {
  private final Project myProject;

  ChangelistConflictAccessProvider(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> requestWriting(@NotNull Collection<? extends VirtualFile> files) {
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    ChangelistConflictTracker conflictTracker = ChangelistConflictTracker.getInstance(myProject);
    ChangelistConflictTracker.Options options = conflictTracker.getOptions();
    if (!options.SHOW_DIALOG) {
      return Collections.emptyList();
    }
    ArrayList<VirtualFile> denied = new ArrayList<>();
    for (VirtualFile file : files) {
      if (file != null && !conflictTracker.isWritingAllowed(file)) {
        denied.add(file);
      }
    }

    if (!denied.isEmpty()) {
      HashSet<ChangeList> changeLists = new HashSet<>();
      ArrayList<Change> changes = new ArrayList<>();
      for (VirtualFile file : denied) {
        changeLists.add(changeListManager.getChangeList(file));
        changes.add(changeListManager.getChange(file));
      }

      ChangelistConflictDialog dialog;
      final int savedEventCount = IdeEventQueue.getInstance().getEventCount();
      do {
        final List<Pair<VirtualFile, Icon>> conflicts = ActionUtil.underModalProgress(
          myProject,
          FileTypesBundle.message("progress.title.resolving.filetype"),
          () -> ContainerUtil.map(denied, (vf) -> new Pair<>(vf, VirtualFilePresentation.getIcon(vf)))
        );
        dialog = new ChangelistConflictDialog(myProject, new ArrayList<>(changeLists), conflicts);
        dialog.show();
      } while (dialog.isOK() && !dialog.getResolution().resolveConflict(myProject, changes, null));
      IdeEventQueue.getInstance().setEventCount(savedEventCount);

      if (dialog.isOK()) {
        options.LAST_RESOLUTION = dialog.getResolution();
        return Collections.emptyList();
      }
    }
    return denied;
  }
}
