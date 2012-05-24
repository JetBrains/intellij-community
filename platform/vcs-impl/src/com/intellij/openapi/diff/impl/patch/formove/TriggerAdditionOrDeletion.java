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

import com.intellij.openapi.diagnostic.Logger;
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
  private final boolean mySilentAddDelete;
  private ProjectLevelVcsManager myVcsManager;
  private AbstractVcsHelper myVcsHelper;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.patch.formove.TriggerAdditionOrDeletion");
  private final VcsFileListenerContextHelper myVcsFileListenerContextHelper;

  private MultiMap<VcsRoot, FilePath> myPreparedAddition;
  private MultiMap<VcsRoot, FilePath> myPreparedDeletion;

  public TriggerAdditionOrDeletion(final Project project, boolean silentAddDelete) {
    myProject = project;
    mySilentAddDelete = silentAddDelete;
    myExisting = new HashSet<FilePath>();
    myDeleted = new HashSet<FilePath>();
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myVcsHelper = AbstractVcsHelper.getInstance(myProject);
    myAffected = new HashSet<FilePath>();
    myVcsFileListenerContextHelper = VcsFileListenerContextHelper.getInstance(myProject);
  }

  public void addExisting(final Collection<FilePath> files) {
    myExisting.addAll(files);
    logFiles("FOR ADD", files);
  }

  public void addDeleted(final Collection<FilePath> files) {
    myDeleted.addAll(files);
    logFiles("FOR DELETION", files);
  }
  
  private void logFiles(final String name, final Collection<FilePath> files) {
    /*final StringBuilder sb = new StringBuilder(name);
    sb.append(": ");
    for (FilePath file : files) {
      sb.append(file.getPath()).append('\n');
    }
    sb.append('\n');
    LOG.info(sb.toString());*/
  }

  public void prepare() {
    if (myExisting.isEmpty() && myDeleted.isEmpty()) return;
    
    final SortByVcsRoots<FilePath> sortByVcsRoots = new SortByVcsRoots<FilePath>(myProject, new Convertor.IntoSelf());

    if (! myExisting.isEmpty()) {
      processAddition(sortByVcsRoots);
    }
    if (! myDeleted.isEmpty()) {
      processDeletion(sortByVcsRoots);
    }
  }

  public void processIt() {
    if (myPreparedDeletion != null) {
      for (Map.Entry<VcsRoot, Collection<FilePath>> entry : myPreparedDeletion.entrySet()) {
        final VcsRoot vcsRoot = entry.getKey();
        final CheckinEnvironment localChangesProvider = vcsRoot.getVcs().getCheckinEnvironment();
        if (localChangesProvider == null) continue;
        final Collection<FilePath> filePaths = entry.getValue();
        if (vcsRoot.getVcs().fileListenerIsSynchronous()) {
          myAffected.addAll(filePaths);
          continue;
        }
        askUserIfNeededDeletion(vcsRoot.getVcs(), (List<FilePath>)filePaths);
        myAffected.addAll(filePaths);
        localChangesProvider.scheduleMissingFileForDeletion((List<FilePath>)filePaths);
      }
    }
    if (myPreparedAddition != null) {
      for (Map.Entry<VcsRoot, Collection<FilePath>> entry : myPreparedAddition.entrySet()) {
        final VcsRoot vcsRoot = entry.getKey();
        final CheckinEnvironment localChangesProvider = vcsRoot.getVcs().getCheckinEnvironment();
        if (localChangesProvider == null) continue;
        final Collection<FilePath> filePaths = entry.getValue();
        if (vcsRoot.getVcs().fileListenerIsSynchronous()) {
          myAffected.addAll(filePaths);
          continue;
        }
        askUserIfNeededAddition(vcsRoot.getVcs(), (List<FilePath>)filePaths);
        myAffected.addAll(filePaths);
        localChangesProvider.scheduleUnversionedFilesForAddition(ObjectsConvertor.fp2vf(filePaths));
      }
    }
  }

  public Set<FilePath> getAffected() {
    return myAffected;
  }

  private void processDeletion(SortByVcsRoots<FilePath> sortByVcsRoots) {
    final MultiMap<VcsRoot, FilePath> map = sortByVcsRoots.sort(myDeleted);
    myPreparedDeletion = new MultiMap<VcsRoot, FilePath>();
    for (VcsRoot vcsRoot : map.keySet()) {
      if (vcsRoot != null && vcsRoot.getVcs() != null) {
        final CheckinEnvironment localChangesProvider = vcsRoot.getVcs().getCheckinEnvironment();
        if (localChangesProvider == null) continue;
        final boolean takeDirs = vcsRoot.getVcs().areDirectoriesVersionedItems();

        final Collection<FilePath> files = map.get(vcsRoot);
        final List<FilePath> toBeDeleted = new LinkedList<FilePath>();
        for (FilePath file : files) {
          final FilePath parent = file.getParentPath();
          if ((takeDirs || (! file.isDirectory())) && parent != null && parent.getIOFile().exists()) {
            toBeDeleted.add(file);
          }
        }
        if (toBeDeleted.isEmpty()) return;
        if (! vcsRoot.getVcs().fileListenerIsSynchronous()) {
          for (FilePath filePath : toBeDeleted) {
            myVcsFileListenerContextHelper.ignoreDeleted(filePath);
          }
        }
        myPreparedDeletion.put(vcsRoot, toBeDeleted);
      }
    }
  }

  private void processAddition(SortByVcsRoots<FilePath> sortByVcsRoots) {
    for (FilePath filePath : myExisting) {
      filePath.hardRefresh();
    }
    final MultiMap<VcsRoot, FilePath> map = sortByVcsRoots.sort(myExisting);
    myPreparedAddition = new MultiMap<VcsRoot, FilePath>();
    for (VcsRoot vcsRoot : map.keySet()) {
      if (vcsRoot != null && vcsRoot.getVcs() != null) {
        final CheckinEnvironment localChangesProvider = vcsRoot.getVcs().getCheckinEnvironment();
        if (localChangesProvider == null) continue;
        final boolean takeDirs = vcsRoot.getVcs().areDirectoriesVersionedItems();

        final Collection<FilePath> files = map.get(vcsRoot);
        final List<FilePath> toBeAdded;
        if (takeDirs) {
          final RecursiveCheckAdder adder = new RecursiveCheckAdder(vcsRoot.getPath());
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
        if (toBeAdded.isEmpty()) {
          return;
        }
        Collections.sort(toBeAdded, FilePathByPathComparator.getInstance());
        if (! vcsRoot.getVcs().fileListenerIsSynchronous()) {
          for (FilePath filePath : toBeAdded) {
            myVcsFileListenerContextHelper.ignoreAdded(filePath.getVirtualFile());
          }
        }
        myPreparedAddition.put(vcsRoot, toBeAdded);
      }
    }
  }

  private void askUserIfNeededAddition(final AbstractVcs vcs, final List<FilePath> toBeAdded) {
    if (mySilentAddDelete) return;
    final VcsShowConfirmationOption confirmationOption = myVcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, vcs);
    if (VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY.equals(confirmationOption.getValue())) {
      toBeAdded.clear();
    } else if (VcsShowConfirmationOption.Value.SHOW_CONFIRMATION.equals(confirmationOption.getValue())) {
      final Collection<FilePath> files = myVcsHelper.selectFilePathsToProcess(toBeAdded, "Select files to add to " + vcs.getDisplayName(), null,
        "Schedule for addition", "Do you want to schedule the following file for addition to " + vcs.getDisplayName() + "\n{0}", confirmationOption);
      if (files == null) {
        toBeAdded.clear();
      } else {
        toBeAdded.retainAll(files);
      }
    }
  }

  private void askUserIfNeededDeletion(final AbstractVcs vcs, final List<FilePath> toBeDeleted) {
    if (mySilentAddDelete) return;
    final VcsShowConfirmationOption confirmationOption = myVcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, vcs);
    if (VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY.equals(confirmationOption.getValue())) {
      toBeDeleted.clear();
    } else if (VcsShowConfirmationOption.Value.SHOW_CONFIRMATION.equals(confirmationOption.getValue())) {
      final Collection<FilePath> files = myVcsHelper.selectFilePathsToProcess(toBeDeleted, "Select files to remove from " + vcs.getDisplayName(), null,
        "Schedule for deletion", "Do you want to schedule the following file for deletion from " + vcs.getDisplayName() + "\n{0}", confirmationOption);
      if (files == null) {
        toBeDeleted.clear();
      } else {
        toBeDeleted.retainAll(files);
      }
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
        VirtualFile vf = current.getVirtualFile();
        if (vf == null) {
          current.hardRefresh();
          vf = current.getVirtualFile();
          if (vf == null) {
            return;
          }
        }
        if (! VfsUtil.isAncestor(myRoot, vf, true)) return;

        myToBeAdded.add(current);
        current = current.getParentPath();
      }
    }

    public List<FilePath> getToBeAdded() {
      return new ArrayList<FilePath>(myToBeAdded);
    }
  }
}
