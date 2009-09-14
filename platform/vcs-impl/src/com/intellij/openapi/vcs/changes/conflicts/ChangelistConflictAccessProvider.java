package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.readOnlyHandler.WritingAccessProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Collections;

/**
 * @author Dmitry Avdeev
 */
public class ChangelistConflictAccessProvider implements WritingAccessProvider {

  private final Project myProject;
  private final ChangeListManagerImpl myManager;

  public ChangelistConflictAccessProvider(Project project, ChangeListManagerImpl manager) {
    myProject = project;
    myManager = manager;
  }

  @NotNull
  public Collection<VirtualFile> requestWriting(VirtualFile... files) {
    ChangelistConflictTracker.Options options = myManager.getConflictTracker().getOptions();
    if (!options.TRACKING_ENABLED || !options.SHOW_DIALOG) {
      return Collections.emptyList();
    }
    ArrayList<VirtualFile> denied = new ArrayList<VirtualFile>();
    for (VirtualFile file : files) {
      if (file != null && !myManager.getConflictTracker().isWritingAllowed(file)) {
        denied.add(file);
      }
    }

    if (!denied.isEmpty()) {
      HashSet<ChangeList> changeLists = new HashSet<ChangeList>();
      ArrayList<Change> changes = new ArrayList<Change>();
      for (VirtualFile file : denied) {
        changeLists.add(myManager.getChangeList(file));
        changes.add(myManager.getChange(file));
      }
      ChangelistConflictDialog dialog = new ChangelistConflictDialog(myProject, new ArrayList<ChangeList>(changeLists), changes);
      dialog.show();
      if (dialog.isOK()) {
        ChangelistConflictResolution resolution = dialog.getResolution();
        options.LAST_RESOLUTION = resolution;
        resolution.resolveConflict(myProject, resolution == ChangelistConflictResolution.SWITCH ? changes : dialog.getSelectedChanges());
      }
    }
    return denied;
  }
}
