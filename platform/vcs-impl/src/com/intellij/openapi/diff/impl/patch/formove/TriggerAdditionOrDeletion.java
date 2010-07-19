/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.patch.formove;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.SortByVcsRoots;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FilePathByPathComparator;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;

import java.util.*;

public class TriggerAdditionOrDeletion {
  private final Collection<FilePath> myExisting;
  private final Collection<FilePath> myDeleted;
  private final Set<FilePath> myAffected;
  private final Project myProject;
  private ProjectLevelVcsManager myVcsManager;
  private AbstractVcsHelper myVcsHelper;

  public TriggerAdditionOrDeletion(final Project project) {
    myProject = project;
    myExisting = new HashSet<FilePath>();
    myDeleted = new HashSet<FilePath>();
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myVcsHelper = AbstractVcsHelper.getInstance(myProject);
    myAffected = new HashSet<FilePath>();
  }

  public void addExisting(final Collection<FilePath> files) {
    myExisting.addAll(files);
  }

  public void addDeleted(final Collection<FilePath> files) {
    myDeleted.addAll(files);
  }

  public void process() {
    if (myExisting.isEmpty() && myDeleted.isEmpty()) return;
    
    final SortByVcsRoots<FilePath> sortByVcsRoots = new SortByVcsRoots<FilePath>(myProject, new Convertor.IntoSelf());
    final ChangeListManager clManager = ChangeListManager.getInstance(myProject);

    if (! myExisting.isEmpty()) {
      processAddition(sortByVcsRoots);
    }
    if (! myDeleted.isEmpty()) {
      processDeletion(sortByVcsRoots);
    }
  }

  public Set<FilePath> getAffected() {
    return myAffected;
  }

  private void processDeletion(SortByVcsRoots<FilePath> sortByVcsRoots) {
    final MultiMap<VcsRoot, FilePath> map = sortByVcsRoots.sort(myDeleted);
    for (VcsRoot vcsRoot : map.keySet()) {
      if (vcsRoot != null && vcsRoot.vcs != null) {
        final CheckinEnvironment localChangesProvider = vcsRoot.vcs.getCheckinEnvironment();
        if (localChangesProvider == null) continue;
        final boolean takeDirs = vcsRoot.vcs.areDirectoriesVersionedItems();

        final Collection<FilePath> files = map.get(vcsRoot);
        final List<FilePath> toBeDeleted = new LinkedList<FilePath>();
        for (FilePath file : files) {
          final FilePath parent = file.getParentPath();
          if ((takeDirs || (! file.isDirectory())) && parent != null && parent.getIOFile().exists()) {
            toBeDeleted.add(file);
          }
        }
        if (toBeDeleted.isEmpty()) return;
        askUserIfNeededDeletion(vcsRoot.vcs, toBeDeleted);
        myAffected.addAll(toBeDeleted);
        localChangesProvider.scheduleMissingFileForDeletion(toBeDeleted);
      }
    }
  }

  private void processAddition(SortByVcsRoots<FilePath> sortByVcsRoots) {
    final MultiMap<VcsRoot, FilePath> map = sortByVcsRoots.sort(myExisting);
    for (VcsRoot vcsRoot : map.keySet()) {
      if (vcsRoot != null && vcsRoot.vcs != null) {
        final CheckinEnvironment localChangesProvider = vcsRoot.vcs.getCheckinEnvironment();
        if (localChangesProvider == null) continue;
        final boolean takeDirs = vcsRoot.vcs.areDirectoriesVersionedItems();

        final Collection<FilePath> files = map.get(vcsRoot);
        final List<FilePath> toBeAdded;
        if (takeDirs) {
          final RecursiveCheckAdder adder = new RecursiveCheckAdder(vcsRoot.path);
          for (FilePath file : files) {
            adder.process(file);
          }
          toBeAdded = adder.getToBeAdded();
        } else {
          toBeAdded = new LinkedList<FilePath>();
          for (FilePath file : files) {
            if (! file.isDirectory()) {
              toBeAdded.add(file);
            }
          }
        }
        if (toBeAdded.isEmpty()) return;
        Collections.sort(toBeAdded, FilePathByPathComparator.getInstance());

        askUserIfNeededAddition(vcsRoot.vcs, toBeAdded);
        myAffected.addAll(toBeAdded);
        localChangesProvider.scheduleUnversionedFilesForAddition(ObjectsConvertor.fp2vf(toBeAdded));
      }
    }
  }

  private void askUserIfNeededAddition(final AbstractVcs vcs, final List<FilePath> toBeAdded) {
    final VcsShowConfirmationOption confirmationOption = myVcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, vcs);
    if (VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY.equals(confirmationOption.getValue())) {
      toBeAdded.clear();
    } else if (VcsShowConfirmationOption.Value.SHOW_CONFIRMATION.equals(confirmationOption.getValue())) {
      final Collection<FilePath> files = myVcsHelper.selectFilePathsToProcess(toBeAdded, "Select files to add to " + vcs.getDisplayName(), null,
        "Schedule for addition", "Do you want to schedule the following file for addition to " + vcs.getDisplayName() + "\n{0}", confirmationOption);
      toBeAdded.retainAll(files);
    }
  }

  private void askUserIfNeededDeletion(final AbstractVcs vcs, final List<FilePath> toBeDeleted) {
    final VcsShowConfirmationOption confirmationOption = myVcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, vcs);
    if (VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY.equals(confirmationOption.getValue())) {
      toBeDeleted.clear();
    } else if (VcsShowConfirmationOption.Value.SHOW_CONFIRMATION.equals(confirmationOption.getValue())) {
      final Collection<FilePath> files = myVcsHelper.selectFilePathsToProcess(toBeDeleted, "Select files to remove from " + vcs.getDisplayName(), null,
        "Schedule for deletion", "Do you want to schedule the following file for deletion from " + vcs.getDisplayName() + "\n{0}", confirmationOption);
      toBeDeleted.retainAll(files);
    }
  }

  private class RecursiveCheckAdder {
    private final Set<FilePath> myToBeAdded;
    private ChangeListManager myChangeListManager;
    private final VirtualFile myRoot;

    private RecursiveCheckAdder(final VirtualFile root) {
      myRoot = root;
      myToBeAdded = new HashSet<FilePath>();
      myChangeListManager = ChangeListManager.getInstance(myProject);
    }

    public void process(final FilePath path) {
      FilePath current = path;
      while (current != null) {
        final VirtualFile vf = current.getVirtualFile();
        if (vf == null) return;
        if (! VfsUtil.isAncestor(myRoot, vf, true)) return;

        final FileStatus status = myChangeListManager.getStatus(vf);
        if (FileStatus.UNKNOWN.equals(status)) {
          myToBeAdded.add(current);
          current = current.getParentPath();
        } else {
          return;
        }
      }
    }

    public List<FilePath> getToBeAdded() {
      return new ArrayList<FilePath>(myToBeAdded);
    }
  }
}
