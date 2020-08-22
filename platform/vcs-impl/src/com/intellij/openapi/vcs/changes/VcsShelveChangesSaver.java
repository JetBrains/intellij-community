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
import com.intellij.openapi.vcs.impl.LocalChangesUnderRoots;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.vcs.changes.ChangeListUtil.createSystemShelvedChangeListName;

public class VcsShelveChangesSaver {
  private static final Logger LOG = Logger.getInstance(VcsShelveChangesSaver.class);
  private final Project project;
  private final String myStashMessage;
  private final ProgressIndicator myProgressIndicator;
  private Map<String, ShelvedChangeList> myShelvedLists;

  public VcsShelveChangesSaver(@NotNull Project project,
                               @NotNull ProgressIndicator indicator,
                               @NotNull String stashMessage) {
    this.project = project;
    myProgressIndicator = indicator;
    myStashMessage = stashMessage;
  }

  @Nullable
  public Map<String, ShelvedChangeList> getShelvedLists() {
    return myShelvedLists;
  }

  public void save(@NotNull Collection<? extends VirtualFile> rootsToSave) throws VcsException {
    LOG.info("save " + rootsToSave);
    Map<String, Map<VirtualFile, Collection<Change>>> lists = LocalChangesUnderRoots.getChangesByLists(rootsToSave, project);

    String oldProgressTitle = myProgressIndicator.getText();
    myProgressIndicator.setText(VcsBundle.getString("vcs.shelving.changes"));
    List<VcsException> exceptions = new ArrayList<>(1);
    myShelvedLists = new HashMap<>();

    for (Map.Entry<String, Map<VirtualFile, Collection<Change>>> entry : lists.entrySet()) {
      final Map<VirtualFile, Collection<Change>> map = entry.getValue();
      final Set<Change> changes = new HashSet<>();
      for (Collection<Change> changeCollection : map.values()) {
        changes.addAll(changeCollection);
      }
      if (!changes.isEmpty()) {
        String name = createSystemShelvedChangeListName(myStashMessage, entry.getKey());
        ShelvedChangeList list = VcsShelveUtils.shelveChanges(project, ShelveChangesManager.getInstance(project), changes, name, exceptions, false, true);
        myShelvedLists.put(entry.getKey(), list);
      }
    }
    if (! exceptions.isEmpty()) {
      LOG.info("save " + exceptions, exceptions.get(0));
      myShelvedLists = null;  // no restore here since during shelving changes are not rolled back...
      throw exceptions.get(0);
    } else {
     doRollback(rootsToSave);
    }
    myProgressIndicator.setText(oldProgressTitle);
  }

  public void load() {
    if (myShelvedLists != null) {
      LOG.info("load ");
      String oldProgressTitle = myProgressIndicator.getText();
      myProgressIndicator.setText(VcsBundle.getString("vcs.unshelving.changes"));
      for (Map.Entry<String, ShelvedChangeList> listEntry : myShelvedLists.entrySet()) {
        VcsShelveUtils
          .doSystemUnshelve(project, listEntry.getValue(), ChangeListManagerImpl.getInstanceImpl(project).findChangeList(listEntry.getKey()), ShelveChangesManager.getInstance(
            project),
                            VcsBundle.getString("vcs.unshelving.conflict.left"),
                            VcsBundle.getString("vcs.unshelving.conflict.right"));
      }
      myProgressIndicator.setText(oldProgressTitle);
    }
  }

  protected void doRollback(@NotNull Collection<? extends VirtualFile> rootsToSave) {
    Set<VirtualFile> rootsSet = new HashSet<>(rootsToSave);
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    List<Change> changes4Rollback = ContainerUtil.filter(ChangeListManagerImpl.getInstanceImpl(project).getAllChanges(), change -> {
      return rootsSet.contains(vcsManager.getVcsRootFor(ChangesUtil.getFilePath(change)));
    });
    new RollbackWorker(project, myStashMessage, true).doRollback(changes4Rollback, true);
  }
}
