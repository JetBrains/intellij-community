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
import static java.util.Objects.requireNonNull;

public class TriggerAdditionOrDeletion {
  private static final Logger LOG = Logger.getInstance(TriggerAdditionOrDeletion.class);

  private final Project myProject;
  private final VcsFileListenerContextHelper myVcsFileListenerContextHelper;

  private final Set<FilePath> myExisting = new HashSet<>();
  private final Set<FilePath> myDeleted = new HashSet<>();
  private final Set<FilePath> myAffected = new HashSet<>();

  private final Map<AbstractVcs, List<FilePath>> myPreparedAddition = new HashMap<>();
  private final Map<AbstractVcs, List<FilePath>> myPreparedDeletion = new HashMap<>();

  public TriggerAdditionOrDeletion(@NotNull Project project) {
    myProject = project;
    myVcsFileListenerContextHelper = VcsFileListenerContextHelper.getInstance(myProject);
  }

  public void addExisting(@NotNull Collection<? extends FilePath> files) {
    myExisting.addAll(files);
  }

  public void addDeleted(@NotNull Collection<? extends FilePath> files) {
    myDeleted.addAll(files);
  }

  public Set<FilePath> getAffected() {
    return myAffected;
  }

  public void prepare() {
    if (!myExisting.isEmpty()) {
      processAddition();
    }
    if (!myDeleted.isEmpty()) {
      processDeletion();
    }
  }

  public void processIt() {
    for (Map.Entry<AbstractVcs, List<FilePath>> entry : myPreparedDeletion.entrySet()) {
      final AbstractVcs vcs = entry.getKey();
      final CheckinEnvironment localChangesProvider = requireNonNull(vcs.getCheckinEnvironment());
      final List<FilePath> filePaths = entry.getValue();

      localChangesProvider.scheduleMissingFileForDeletion(filePaths);
    }
    final List<FilePath> incorrectFilePath = new ArrayList<>();
    for (Map.Entry<AbstractVcs, List<FilePath>> entry : myPreparedAddition.entrySet()) {
      final AbstractVcs vcs = entry.getKey();
      final CheckinEnvironment localChangesProvider = requireNonNull(vcs.getCheckinEnvironment());
      final List<FilePath> filePaths = entry.getValue();

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

  private void notifyAndLogFiles(@NotNull List<FilePath> incorrectFilePath) {
    String message = VcsBundle.message("patch.apply.incorrectly.processed.warning", incorrectFilePath.size(), incorrectFilePath);
    LOG.warn(message);
    VcsNotifier.getInstance(myProject).notifyImportantWarning(VcsBundle.message("patch.apply.new.files.warning"), message);
  }

  private void processDeletion() {
    Map<VcsRoot, List<FilePath>> map = groupByRoots(myProject, myDeleted, identity());

    for (VcsRoot vcsRoot : map.keySet()) {
      AbstractVcs vcs = vcsRoot.getVcs();
      if (vcs == null) continue;

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
      myAffected.addAll(toBeDeleted);

      if (!vcs.fileListenerIsSynchronous()) {
        for (FilePath filePath : toBeDeleted) {
          myVcsFileListenerContextHelper.ignoreDeleted(filePath);
        }

        List<FilePath> paths = myPreparedDeletion.computeIfAbsent(vcs, key -> new ArrayList<>());
        paths.addAll(toBeDeleted);
      }
    }
  }

  private void processAddition() {
    Map<VcsRoot, List<FilePath>> map = groupByRoots(myProject, myExisting, identity());

    for (VcsRoot vcsRoot : map.keySet()) {
      AbstractVcs vcs = vcsRoot.getVcs();
      if (vcs == null) continue;

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

      if (toBeAdded.isEmpty()) return;
      myAffected.addAll(toBeAdded);

      if (!vcs.fileListenerIsSynchronous()) {
        for (FilePath filePath : ContainerUtil.sorted(toBeAdded, FilePathByPathComparator.getInstance())) {
          myVcsFileListenerContextHelper.ignoreAdded(filePath.getVirtualFile());
        }

        List<FilePath> paths = myPreparedAddition.computeIfAbsent(vcs, key -> new ArrayList<>());
        paths.addAll(toBeAdded);
      }
    }
  }

  private static class RecursiveCheckAdder {
    private final Set<FilePath> myToBeAdded = new HashSet<>();
    private final VirtualFile myRoot;

    private RecursiveCheckAdder(final VirtualFile root) {
      myRoot = root;
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
