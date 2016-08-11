/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.WritingAccessProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Collections;

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
  public Collection<VirtualFile> requestWriting(VirtualFile... files) {
    ChangelistConflictTracker.Options options = myManager.getConflictTracker().getOptions();
    if (!options.TRACKING_ENABLED || !options.SHOW_DIALOG) {
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
      } while (dialog.isOK() && !dialog.getResolution().resolveConflict(myProject, changes));
      IdeEventQueue.getInstance().setEventCount(savedEventCount);

      if (dialog.isOK()) {
        options.LAST_RESOLUTION = dialog.getResolution();
        return Collections.emptyList();
      }
    }
    return denied;
  }

  @Override
  public boolean isPotentiallyWritable(@NotNull final VirtualFile file) {
    return true;
  }
}
