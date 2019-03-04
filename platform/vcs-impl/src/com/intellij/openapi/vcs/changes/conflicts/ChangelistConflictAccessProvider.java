// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * @author Dmitry Avdeev
 */
public class ChangelistConflictAccessProvider extends WritingAccessProvider {
  private final Project myProject;
  private final ChangeListManagerImpl myManager;

  public ChangelistConflictAccessProvider(Project project, ChangeListManagerImpl manager) {
    myProject = project;
    myManager = manager;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> requestWriting(@NotNull Collection<? extends VirtualFile> files) {
    ChangelistConflictTracker.Options options = myManager.getConflictTracker().getOptions();
    if (!options.SHOW_DIALOG) {
      return Collections.emptyList();
    }
    ArrayList<VirtualFile> denied = new ArrayList<>();
    for (VirtualFile file : files) {
      if (file != null && !myManager.getConflictTracker().isWritingAllowed(file)) {
        denied.add(file);
      }
    }

    if (!denied.isEmpty()) {
      HashSet<ChangeList> changeLists = new HashSet<>();
      ArrayList<Change> changes = new ArrayList<>();
      for (VirtualFile file : denied) {
        changeLists.add(myManager.getChangeList(file));
        changes.add(myManager.getChange(file));
      }

      ChangelistConflictDialog dialog;
      final int savedEventCount = IdeEventQueue.getInstance().getEventCount();
      do {
        dialog = new ChangelistConflictDialog(myProject, new ArrayList<>(changeLists), denied);
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
