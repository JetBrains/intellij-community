// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.Processor;
import com.intellij.util.StringLenComparator;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VcsRootIterator {
  // folder path to files to be excluded
  private final Map<String, MyRootFilter> myOtherVcsFolders;
  private final FileIndexFacade myExcludedFileIndex;
  private final ProjectLevelVcsManager myVcsManager;
  private final Project myProject;

  public VcsRootIterator(final Project project, final AbstractVcs vcs) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myOtherVcsFolders = new HashMap<>();
    myExcludedFileIndex = FileIndexFacade.getInstance(project);

    final VcsRoot[] allRoots = myVcsManager.getAllVcsRoots();
    final VirtualFile[] roots = myVcsManager.getRootsUnderVcs(vcs);
    for (VirtualFile root : roots) {
      final MyRootFilter rootPresentFilter = new MyRootFilter(root, vcs.getName());
      rootPresentFilter.init(allRoots);
      myOtherVcsFolders.put(root.getUrl(), rootPresentFilter);
    }
  }

  public boolean acceptFolderUnderVcs(final VirtualFile vcsRoot, final VirtualFile file) {
    final String vcsUrl = vcsRoot.getUrl();
    final MyRootFilter rootFilter = myOtherVcsFolders.get(vcsUrl);
    if ((rootFilter != null) && (!rootFilter.accept(file))) {
      return false;
    }
    return !isIgnoredByVcs(myVcsManager, myProject, file);
  }

  private static boolean isIgnoredByVcs(final ProjectLevelVcsManager vcsManager, final Project project, final VirtualFile file) {
    return ReadAction.compute(() -> project.isDisposed() || vcsManager.isIgnored(file));
  }

  private static final class MyRootFilter {
    private final VirtualFile myRoot;
    private final String myVcsName;

    // virtual file URLs
    private final List<String> myExcludedByOthers;

    private MyRootFilter(final VirtualFile root, final String vcsName) {
      myRoot = root;
      myVcsName = vcsName;

      myExcludedByOthers = new ArrayList<>();
    }

    private void init(final VcsRoot[] allRoots) {
      final String ourPath = myRoot.getUrl();

      for (VcsRoot root : allRoots) {
        final AbstractVcs vcs = root.getVcs();
        if (vcs == null || Objects.equals(vcs.getName(), myVcsName)) continue;
        final String url = root.getPath().getUrl();
        if (url.startsWith(ourPath)) {
          myExcludedByOthers.add(url);
        }
      }

      myExcludedByOthers.sort(StringLenComparator.getDescendingInstance());
    }

    public boolean accept(final VirtualFile vf) {
      final String url = vf.getUrl();
      for (String excludedByOtherVcs : myExcludedByOthers) {
        // use the fact that they are sorted
        if (url.length() > excludedByOtherVcs.length()) return true;
        if (url.startsWith(excludedByOtherVcs)) return false;
      }
      return true;
    }
  }

  public static void iterateVfUnderVcsRoot(final Project project,
                                           final VirtualFile root,
                                           final Processor<? super VirtualFile> processor) {
    final MyRootIterator rootIterator = new MyRootIterator(project, root, null, processor, null);
    rootIterator.iterate();
  }

  public static void iterateVcsRoot(final Project project,
                                    final VirtualFile root,
                                    final Processor<? super FilePath> processor) {
    iterateVcsRoot(project, root, processor, null);
  }

  public static void iterateVcsRoot(final Project project,
                                    final VirtualFile root,
                                    final Processor<? super FilePath> processor,
                                    @Nullable VirtualFileFilter directoryFilter) {
    final MyRootIterator rootIterator = new MyRootIterator(project, root, processor, null, directoryFilter);
    rootIterator.iterate();
  }

  private static final class MyRootIterator {
    private final Project myProject;
    private final Processor<? super FilePath> myPathProcessor;
    private final Processor<? super VirtualFile> myFileProcessor;
    private final @Nullable VirtualFileFilter myDirectoryFilter;
    private final VirtualFile myRoot;
    private final MyRootFilter myRootPresentFilter;
    private final ProjectLevelVcsManager myVcsManager;

    private MyRootIterator(final Project project,
                           final VirtualFile root,
                           final @Nullable Processor<? super FilePath> pathProcessor,
                           final @Nullable Processor<? super VirtualFile> fileProcessor,
                           @Nullable VirtualFileFilter directoryFilter) {
      myProject = project;
      myPathProcessor = pathProcessor;
      myFileProcessor = fileProcessor;
      myDirectoryFilter = directoryFilter;
      myRoot = root;

      myVcsManager = ProjectLevelVcsManager.getInstance(project);
      final AbstractVcs vcs = myVcsManager.getVcsFor(root);
      myRootPresentFilter = vcs == null ? null : new MyRootFilter(root, vcs.getName());
      if (myRootPresentFilter != null) {
        myRootPresentFilter.init(myVcsManager.getAllVcsRoots());
      }
    }

    public void iterate() {
      VfsUtilCore.visitChildrenRecursively(myRoot, new VirtualFileVisitor<Void>(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
        @Override
        public void afterChildrenVisited(@NotNull VirtualFile file) {
          if (myDirectoryFilter != null) {
            myDirectoryFilter.afterChildrenVisited(file);
          }
        }

        @Override
        public @NotNull Result visitFileEx(@NotNull VirtualFile file) {
          if (isIgnoredByVcs(myVcsManager, myProject, file)) return SKIP_CHILDREN;
          if (myRootPresentFilter != null && !myRootPresentFilter.accept(file)) return SKIP_CHILDREN;
          if (myProject.isDisposed() || !process(file)) return skipTo(myRoot);
          if (myDirectoryFilter != null && file.isDirectory() && !myDirectoryFilter.shouldGoIntoDirectory(file)) return SKIP_CHILDREN;
          return CONTINUE;
        }
      });
    }

    private boolean process(VirtualFile current) {
      if (myPathProcessor != null) {
        return myPathProcessor.process(VcsUtil.getFilePath(current));
      }
      else if (myFileProcessor != null) {
        return myFileProcessor.process(current);
      }
      return false;
    }
  }

  /**
   * Invoke the {@code iterator} for all files in the dirty scope.
   * For recursively dirty directories all children are processed.
   */
  public static void iterate(@NotNull VcsDirtyScope scope, @NotNull Processor<? super FilePath> iterator) {
    Project project = scope.getProject();
    if (project.isDisposed()) return;

    for (FilePath dir : scope.getRecursivelyDirtyDirectories()) {
      final VirtualFile vFile = dir.getVirtualFile();
      if (vFile != null && vFile.isValid()) {
        iterateVcsRoot(project, vFile, iterator);
      }
    }

    for (FilePath file : scope.getDirtyFilesNoExpand()) {
      iterator.process(file);
      final VirtualFile vFile = file.getVirtualFile();
      if (vFile != null && vFile.isValid() && vFile.isDirectory()) {
        for (VirtualFile child : vFile.getChildren()) {
          iterator.process(VcsUtil.getFilePath(child));
        }
      }
    }
  }

  public static void iterateExistingInsideScope(@NotNull VcsDirtyScope scope, @NotNull Processor<? super VirtualFile> iterator) {
    Project project = scope.getProject();
    if (project.isDisposed()) return;

    for (FilePath dir : scope.getRecursivelyDirtyDirectories()) {
      final VirtualFile vFile = obtainVirtualFile(dir);
      if (vFile != null && vFile.isValid()) {
        iterateVfUnderVcsRoot(project, vFile, iterator);
      }
    }

    for (FilePath file : scope.getDirtyFilesNoExpand()) {
      VirtualFile vFile = obtainVirtualFile(file);
      if (vFile != null && vFile.isValid()) {
        iterator.process(vFile);
        if (vFile.isDirectory()) {
          for (VirtualFile child : vFile.getChildren()) {
            iterator.process(child);
          }
        }
      }
    }
  }

  private static @Nullable VirtualFile obtainVirtualFile(FilePath file) {
    VirtualFile vFile = file.getVirtualFile();
    return vFile == null ? VfsUtil.findFileByIoFile(file.getIOFile(), false) : vFile;
  }
}
