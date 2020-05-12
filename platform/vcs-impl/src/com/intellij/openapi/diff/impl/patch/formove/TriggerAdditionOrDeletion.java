// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch.formove;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FilePathByPathComparator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.util.Functions.identity;
import static com.intellij.vcsUtil.VcsUtil.groupByRoots;

public class TriggerAdditionOrDeletion {
  private final Collection<FilePath> myExisting;
  private final Collection<FilePath> myDeleted;
  private final Set<FilePath> myAffected;
  private final Project myProject;
  private static final Logger LOG = Logger.getInstance(TriggerAdditionOrDeletion.class);
  private final VcsFileListenerContextHelper myVcsFileListenerContextHelper;

  private Map<AbstractVcs, List<FilePath>> myPreparedAddition;
  private Map<AbstractVcs, List<FilePath>> myPreparedDeletion;

  public TriggerAdditionOrDeletion(final Project project) {
    myProject = project;
    myExisting = new HashSet<>();
    myDeleted = new HashSet<>();
    myAffected = new HashSet<>();
    myVcsFileListenerContextHelper = VcsFileListenerContextHelper.getInstance(myProject);
  }

  public void addExisting(final Collection<? extends FilePath> files) {
    myExisting.addAll(files);
  }

  public void addDeleted(final Collection<? extends FilePath> files) {
    myDeleted.addAll(files);
  }

  public void prepare() {
    if (myExisting.isEmpty() && myDeleted.isEmpty()) return;

    if (! myExisting.isEmpty()) {
      processAddition();
    }
    if (! myDeleted.isEmpty()) {
      processDeletion();
    }
  }

  public void processIt() {
    if (myPreparedDeletion != null) {
      for (Map.Entry<AbstractVcs, List<FilePath>> entry : myPreparedDeletion.entrySet()) {
        final AbstractVcs vcs = entry.getKey();
        final CheckinEnvironment localChangesProvider = vcs.getCheckinEnvironment();
        if (localChangesProvider == null) continue;
        final List<FilePath> filePaths = entry.getValue();
        if (vcs.fileListenerIsSynchronous()) {
          myAffected.addAll(filePaths);
          continue;
        }
        myAffected.addAll(filePaths);
       localChangesProvider.scheduleMissingFileForDeletion(filePaths);
      }
    }
    if (myPreparedAddition != null) {
      final List<FilePath> incorrectFilePath = new ArrayList<>();
      for (Map.Entry<AbstractVcs, List<FilePath>> entry : myPreparedAddition.entrySet()) {
        final AbstractVcs vcs = entry.getKey();
        final CheckinEnvironment localChangesProvider = vcs.getCheckinEnvironment();
        if (localChangesProvider == null) continue;
        final List<FilePath> filePaths = entry.getValue();
        if (vcs.fileListenerIsSynchronous()) {
          myAffected.addAll(filePaths);
          continue;
        }
        myAffected.addAll(filePaths);
        final List<VirtualFile> virtualFiles = new ArrayList<>();
        ContainerUtil.process(filePaths, path -> {
          VirtualFile vf = path.getVirtualFile();
          if (vf == null) {
            incorrectFilePath.add(path);
          }
          else {
            virtualFiles.add(vf);
          }
          return true;
        });
        localChangesProvider.scheduleUnversionedFilesForAddition(virtualFiles);
      }
      //if some errors occurred  -> notify
      if (!incorrectFilePath.isEmpty()) {
        notifyAndLogFiles(incorrectFilePath);
      }
    }
  }

  private void notifyAndLogFiles(@NotNull List<FilePath> incorrectFilePath) {
    String message = VcsBundle.message("patch.apply.incorrectly.processed.warning", incorrectFilePath.size(), incorrectFilePath);
    LOG.warn(message);
    VcsNotifier.getInstance(myProject).notifyImportantWarning(VcsBundle.message("patch.apply.new.files.warning"), message);
  }

  public Set<FilePath> getAffected() {
    return myAffected;
  }

  private void processDeletion() {
    Map<VcsRoot, List<FilePath>> map = groupByRoots(myProject, myDeleted, identity());

    myPreparedDeletion = new HashMap<>();
    for (VcsRoot vcsRoot : map.keySet()) {
      if (vcsRoot != null) {
        AbstractVcs vcs = vcsRoot.getVcs();
        if (vcs != null) {
          final CheckinEnvironment localChangesProvider = vcs.getCheckinEnvironment();
          if (localChangesProvider == null) continue;
          final boolean takeDirs = vcs.areDirectoriesVersionedItems();

          final Collection<FilePath> files = map.get(vcsRoot);
          final List<FilePath> toBeDeleted = new ArrayList<>();
          for (FilePath file : files) {
            final FilePath parent = file.getParentPath();
            if ((takeDirs || (!file.isDirectory())) && parent != null && parent.getIOFile().exists()) {
              toBeDeleted.add(file);
            }
          }
          if (toBeDeleted.isEmpty()) return;
          if (!vcs.fileListenerIsSynchronous()) {
            for (FilePath filePath : toBeDeleted) {
              myVcsFileListenerContextHelper.ignoreDeleted(filePath);
            }
          }
          List<FilePath> paths = myPreparedDeletion.computeIfAbsent(vcs, key -> new ArrayList<>());
          paths.addAll(toBeDeleted);
        }
      }
    }
  }

  private void processAddition() {
    Map<VcsRoot, List<FilePath>> map = groupByRoots(myProject, myExisting, identity());

    myPreparedAddition = new HashMap<>();
    for (VcsRoot vcsRoot : map.keySet()) {
      if (vcsRoot != null) {
        AbstractVcs vcs = vcsRoot.getVcs();
        if (vcs != null) {
          final CheckinEnvironment localChangesProvider = vcs.getCheckinEnvironment();
          if (localChangesProvider == null) continue;
          final boolean takeDirs = vcs.areDirectoriesVersionedItems();

          final Collection<FilePath> files = map.get(vcsRoot);
          final List<FilePath> toBeAdded;
          if (takeDirs) {
            final RecursiveCheckAdder adder = new RecursiveCheckAdder(vcsRoot.getPath());
            for (FilePath file : files) {
              adder.process(file);
            }
            toBeAdded = adder.getToBeAdded();
          }
          else {
            toBeAdded = new ArrayList<>();
            for (FilePath file : files) {
              if (!file.isDirectory()) {
                toBeAdded.add(file);
              }
            }
          }
          if (toBeAdded.isEmpty()) {
            return;
          }
          toBeAdded.sort(FilePathByPathComparator.getInstance());
          if (!vcs.fileListenerIsSynchronous()) {
            for (FilePath filePath : toBeAdded) {
              myVcsFileListenerContextHelper.ignoreAdded(filePath.getVirtualFile());
            }
          }
          List<FilePath> paths = myPreparedAddition.computeIfAbsent(vcs, key -> new ArrayList<>());
          paths.addAll(toBeAdded);
        }
      }
    }
  }

  private static class RecursiveCheckAdder {
    private final Set<FilePath> myToBeAdded;
    private final VirtualFile myRoot;

    private RecursiveCheckAdder(final VirtualFile root) {
      myRoot = root;
      myToBeAdded = new HashSet<>();
    }

    public void process(final FilePath path) {
      FilePath current = path;
      while (current != null) {
        VirtualFile vf = current.getVirtualFile();
        if (vf == null) {
          vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(current.getPath());
        }
        if (vf == null) {
          return;
        }
        if (!VfsUtilCore.isAncestor(myRoot, vf, true)) return;

        myToBeAdded.add(current);
        current = current.getParentPath();
      }
    }

    public List<FilePath> getToBeAdded() {
      return new ArrayList<>(myToBeAdded);
    }
  }
}
