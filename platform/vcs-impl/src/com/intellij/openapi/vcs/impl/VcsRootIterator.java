/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.Processor;
import com.intellij.util.StringLenComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VcsRootIterator {
  // folder path to files to be excluded
  private final Map<String, MyRootFilter> myOtherVcsFolders;
  private final FileIndexFacade myExcludedFileIndex;

  public VcsRootIterator(final Project project, final AbstractVcs vcs) {
    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
    myOtherVcsFolders = new HashMap<String, MyRootFilter>();
    myExcludedFileIndex = PeriodicalTasksCloser.getInstance().safeGetService(project, FileIndexFacade.class);

    final VcsRoot[] allRoots = plVcsManager.getAllVcsRoots();
    final VirtualFile[] roots = plVcsManager.getRootsUnderVcs(vcs);
    for (VirtualFile root : roots) {
      final MyRootFilter rootPresentFilter = new MyRootFilter(root, vcs.getName());
      rootPresentFilter.init(allRoots);
      myOtherVcsFolders.put(root.getUrl(), rootPresentFilter);
    }
  }

  public boolean acceptFolderUnderVcs(final VirtualFile vcsRoot, final VirtualFile file) {
    final String vcsUrl = vcsRoot.getUrl();
    final MyRootFilter rootFilter = myOtherVcsFolders.get(vcsUrl);
    if ((rootFilter != null) && (! rootFilter.accept(file))) {
      return false;
    }
    final Boolean excluded = isExcluded(myExcludedFileIndex, file);
    if (excluded) return false;
    return true;
  }

  private static boolean isExcluded(final FileIndexFacade indexFacade, final VirtualFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return indexFacade.isExcludedFile(file);
      }
    });
  }

  private static class MyRootFilter {
    private final VirtualFile myRoot;
    private final String myVcsName;

    // virtual file URLs
    private final List<String> myExcludedByOthers;

    private MyRootFilter(final VirtualFile root, final String vcsName) {
      myRoot = root;
      myVcsName = vcsName;

      myExcludedByOthers = new LinkedList<String>();
    }

    private void init(final VcsRoot[] allRoots) {
      final String ourPath = myRoot.getUrl();

      for (VcsRoot root : allRoots) {
        final AbstractVcs vcs = root.getVcs();
        if (vcs == null || Comparing.equal(vcs.getName(), myVcsName)) continue;
        final VirtualFile path = root.getPath();
        if (path != null) {
          final String url = path.getUrl();
          if (url.startsWith(ourPath)) {
            myExcludedByOthers.add(url);
          }
        }
      }

      Collections.sort(myExcludedByOthers, StringLenComparator.getDescendingInstance());
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
                                           final Processor<VirtualFile> processor) {
    final MyRootIterator rootIterator = new MyRootIterator(project, root, null, processor, null);
    rootIterator.iterate();
  }

  public static void iterateVcsRoot(final Project project,
                                    final VirtualFile root,
                                    final Processor<FilePath> processor) {
    iterateVcsRoot(project, root, processor, null);
  }

  public static void iterateVcsRoot(final Project project,
                                       final VirtualFile root,
                                       final Processor<FilePath> processor,
                                       @Nullable VirtualFileFilter directoryFilter) {
    final MyRootIterator rootIterator = new MyRootIterator(project, root, processor, null, directoryFilter);
    rootIterator.iterate();
  }

  private static class MyRootIterator {
    private final Project myProject;
    private final Processor<FilePath> myPathProcessor;
    private final Processor<VirtualFile> myFileProcessor;
    @Nullable private final VirtualFileFilter myDirectoryFilter;
    private final VirtualFile myRoot;
    private final MyRootFilter myRootPresentFilter;
    private final FileIndexFacade myExcludedFileIndex;

    private MyRootIterator(final Project project,
                           final VirtualFile root,
                           @Nullable final Processor<FilePath> pathProcessor,
                           @Nullable final Processor<VirtualFile> fileProcessor,
                           @Nullable VirtualFileFilter directoryFilter) {
      myProject = project;
      myPathProcessor = pathProcessor;
      myFileProcessor = fileProcessor;
      myDirectoryFilter = directoryFilter;
      myRoot = root;

      final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
      final AbstractVcs vcs = plVcsManager.getVcsFor(root);
      myRootPresentFilter = (vcs == null) ? null : new MyRootFilter(root, vcs.getName());
      if (myRootPresentFilter != null) {
        myRootPresentFilter.init(ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots());
      }
      myExcludedFileIndex = PeriodicalTasksCloser.getInstance().safeGetService(project, FileIndexFacade.class);
    }

    public void iterate() {
      class StopIterationException extends RuntimeException { }

      try {
        VfsUtilCore.visitChildrenRecursively(myRoot, new VirtualFileVisitor(false) {
          @Override
          public void afterChildrenVisited(@NotNull VirtualFile file) {
            if (myDirectoryFilter != null) {
              myDirectoryFilter.afterChildrenVisited(file);
            }
          }

          @Override
          public boolean visitFile(@NotNull VirtualFile file) {
            if (isExcluded(myExcludedFileIndex, file)) return false;
            if (myRootPresentFilter != null && ! myRootPresentFilter.accept(file)) return false;
            if (myProject.isDisposed() || ! process(file)) throw new StopIterationException();
            if (myDirectoryFilter != null && file.isDirectory() && ! myDirectoryFilter.shouldGoIntoDirectory(file)) return false;
            return true;
          }
        });
      } catch (StopIterationException e) {
        //
      }
    }

    private boolean process(VirtualFile current) {
      if (myPathProcessor != null) {
        return myPathProcessor.process(new FilePathImpl(current));
      }
      else if (myFileProcessor != null) {
        return myFileProcessor.process(current);
      }
      return false;
    }
  }
}
