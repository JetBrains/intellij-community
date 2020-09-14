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
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.vcs.changes.ChangeListUtil.createSystemShelvedChangeListName;

public class VcsShelveChangesSaver {
  private static final Logger LOG = Logger.getInstance(VcsShelveChangesSaver.class);
  private final Project project;
  private final @Nls String myStashMessage;
  private final ProgressIndicator myProgressIndicator;
  @Nullable private Map<String, ShelvedChangeList> myShelvedLists; // LocalChangeList.id -> shelved changes

  public VcsShelveChangesSaver(@NotNull Project project,
                               @NotNull ProgressIndicator indicator,
                               @NotNull @Nls String stashMessage) {
    this.project = project;
    myProgressIndicator = indicator;
    myStashMessage = stashMessage;
  }

  @Nullable
  public List<ShelvedChangeList> getShelvedLists() {
    return myShelvedLists != null ? new ArrayList<>(myShelvedLists.values()) : null;
  }

  public void save(@NotNull Collection<? extends VirtualFile> rootsToSave) throws VcsException {
    LOG.info("save " + rootsToSave);

    String oldProgressTitle = myProgressIndicator.getText();
    myProgressIndicator.setText(VcsBundle.getString("vcs.shelving.changes"));

    Map<String, ShelvedChangeList> shelvedLists = new HashMap<>();
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.areChangeListsEnabled()) {
      for (LocalChangeList list : changeListManager.getChangeLists()) {
        Collection<Change> changes = list.getChanges();
        if (!changes.isEmpty()) {
          String name = createSystemShelvedChangeListName(myStashMessage, list.getName());
          ShelvedChangeList shelved = VcsShelveUtils.shelveChanges(project, changes, name, false, true);
          shelvedLists.put(list.getId(), shelved);
        }
      }
    }
    else {
      Collection<Change> changes = changeListManager.getAllChanges();
      if (!changes.isEmpty()) {
        ShelvedChangeList shelved = VcsShelveUtils.shelveChanges(project, changes, myStashMessage, false, true);
        shelvedLists.put(null, shelved);
      }
    }

    myShelvedLists = shelvedLists;
    doRollback(rootsToSave);

    myProgressIndicator.setText(oldProgressTitle);
  }

  public void load() {
    if (myShelvedLists != null) {
      LOG.info("load ");
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
  }

  protected void doRollback(@NotNull Collection<? extends VirtualFile> rootsToSave) {
    Set<VirtualFile> rootsSet = new HashSet<>(rootsToSave);
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    List<Change> changes4Rollback = ContainerUtil.filter(ChangeListManager.getInstance(project).getAllChanges(), change -> {
      return rootsSet.contains(vcsManager.getVcsRootFor(ChangesUtil.getFilePath(change)));
    });
    new RollbackWorker(project, myStashMessage, true).doRollback(changes4Rollback, true);
  }
}
