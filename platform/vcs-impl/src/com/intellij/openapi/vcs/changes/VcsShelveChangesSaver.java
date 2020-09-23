// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.ui.RollbackWorker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.vcs.changes.ChangeListUtil.createSystemShelvedChangeListName;

public class VcsShelveChangesSaver {
  private static final Logger LOG = Logger.getInstance(VcsShelveChangesSaver.class);
  private final Project project;
  private final @Nls String myStashMessage;
  private final ProgressIndicator myProgressIndicator;
  private final Map<String, ShelvedChangeList> myShelvedLists = new HashMap<>(); // LocalChangeList.id -> shelved changes

  public VcsShelveChangesSaver(@NotNull Project project,
                               @NotNull ProgressIndicator indicator,
                               @NotNull @Nls String stashMessage) {
    this.project = project;
    myProgressIndicator = indicator;
    myStashMessage = stashMessage;
  }

  @NotNull
  public List<ShelvedChangeList> getShelvedLists() {
    return new ArrayList<>(myShelvedLists.values());
  }

  public void save(@NotNull Collection<? extends VirtualFile> rootsToSave) throws VcsException {
    LOG.info("save " + rootsToSave);

    String oldProgressTitle = myProgressIndicator.getText();
    myProgressIndicator.setText(VcsBundle.getString("vcs.shelving.changes"));

    Set<VirtualFile> rootsSet = new HashSet<>(rootsToSave);
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.areChangeListsEnabled()) {
      for (LocalChangeList list : changeListManager.getChangeLists()) {
        Collection<Change> changes = filterChangesByRoots(list.getChanges(), rootsSet);
        if (!changes.isEmpty()) {
          String name = createSystemShelvedChangeListName(myStashMessage, list.getName());
          ShelvedChangeList shelved = VcsShelveUtils.shelveChanges(project, changes, name, false, true);
          myShelvedLists.put(list.getId(), shelved);
        }
      }
    }
    else {
      Collection<Change> changes = filterChangesByRoots(changeListManager.getAllChanges(), rootsSet);
      if (!changes.isEmpty()) {
        ShelvedChangeList shelved = VcsShelveUtils.shelveChanges(project, changes, myStashMessage, false, true);
        myShelvedLists.put(null, shelved);
      }
    }

    doRollback(rootsToSave);

    myProgressIndicator.setText(oldProgressTitle);
  }

  public void load() {
    LOG.info("load");
    String oldProgressTitle = myProgressIndicator.getText();
    myProgressIndicator.setText(VcsBundle.getString("vcs.unshelving.changes"));
    for (Map.Entry<String, ShelvedChangeList> listEntry : myShelvedLists.entrySet()) {
      VcsShelveUtils.doSystemUnshelve(project, listEntry.getValue(),
                                      ChangeListManager.getInstance(project).getChangeList(listEntry.getKey()),
                                      ShelveChangesManager.getInstance(project),
                                      VcsBundle.getString("vcs.unshelving.conflict.left"),
                                      VcsBundle.getString("vcs.unshelving.conflict.right"));
    }
    myProgressIndicator.setText(oldProgressTitle);
  }

  protected void doRollback(@NotNull Collection<? extends VirtualFile> rootsToSave) {
    Set<VirtualFile> rootsSet = new HashSet<>(rootsToSave);
    List<Change> changes4Rollback = filterChangesByRoots(ChangeListManager.getInstance(project).getAllChanges(), rootsSet);
    new RollbackWorker(project, myStashMessage, true).doRollback(changes4Rollback, true);
  }

  @NotNull
  private List<Change> filterChangesByRoots(@NotNull Collection<Change> changes, @NotNull Set<? extends VirtualFile> rootsToSave) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    return ContainerUtil.filter(changes, change -> {
      return rootsToSave.contains(vcsManager.getVcsRootFor(ChangesUtil.getFilePath(change)));
    });
  }
}
