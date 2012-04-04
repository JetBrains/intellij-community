/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.StringLenComparator;
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

  public static boolean iterateVfUnderVcsRoot(Project project, VirtualFile file, Processor<VirtualFile> processor) {
    final MyRootIterator rootIterator = new MyRootIterator(project, file, null, processor, null);
    return rootIterator.iterate();
  }

  private static class MyRootFilter {
    private final VirtualFile myRoot;
    private final String myVcsName;

    // virtual file URLs
    private final List<String> myExcludedByOtherVcss;

    private MyRootFilter(final VirtualFile root, final String vcsName) {
      myRoot = root;
      myVcsName = vcsName;

      myExcludedByOtherVcss = new LinkedList<String>();
    }

    private void init(final VcsRoot[] allRoots) {
      final String ourPath = myRoot.getUrl();

      for (VcsRoot root : allRoots) {
        if (Comparing.equal(root.vcs.getName(), myVcsName)) continue;
        final String url = root.path.getUrl();
        if (url.startsWith(ourPath)) {
          myExcludedByOtherVcss.add(url);
        }
      }

      Collections.sort(myExcludedByOtherVcss, StringLenComparator.getDescendingInstance());
    }

    public boolean accept(final VirtualFile vf) {
      final String url = vf.getUrl();
      for (String excludedByOtherVcs : myExcludedByOtherVcss) {
        // use the fact that they are sorted
        if (url.length() > excludedByOtherVcs.length()) return true;
        if (url.startsWith(excludedByOtherVcs)) return false;
      }
      return true;
    }
  }

  public static boolean iterateVcsRoot(final Project project, final VirtualFile root, final Processor<FilePath> processor) {
    return iterateVcsRoot(project, root, processor, null);
  }

  public static boolean iterateVcsRoot(final Project project, final VirtualFile root, final Processor<FilePath> processor,
                                       @Nullable PairProcessor<VirtualFile, VirtualFile[]> directoryFilter) {
    final MyRootIterator rootIterator = new MyRootIterator(project, root, processor, null, directoryFilter);
    return rootIterator.iterate();
  }

  private static class MyRootIterator {
    private final Processor<FilePath> myProcessor;
    private final Processor<VirtualFile> myVfProcessor;
    @Nullable private final PairProcessor<VirtualFile, VirtualFile[]> myDirectoryFilter;
    private final LinkedList<VirtualFile> myQueue;
    private final MyRootFilter myRootPresentFilter;
    private final FileIndexFacade myExcludedFileIndex;

    private MyRootIterator(final Project project, final VirtualFile root, final Processor<FilePath> processor, final Processor<VirtualFile> vfProcessor,
                           @Nullable PairProcessor<VirtualFile, VirtualFile[]> directoryFilter) {
      myProcessor = processor;
      myVfProcessor = vfProcessor;
      myDirectoryFilter = directoryFilter;

      final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
      final AbstractVcs vcs = plVcsManager.getVcsFor(root);
      myRootPresentFilter = (vcs == null) ? null : new MyRootFilter(root, vcs.getName());
      myExcludedFileIndex = PeriodicalTasksCloser.getInstance().safeGetService(project, FileIndexFacade.class);

      myQueue = new LinkedList<VirtualFile>();
      myQueue.add(root);
    }

    public boolean iterate() {
      while (! myQueue.isEmpty()) {
        final VirtualFile current = myQueue.removeFirst();
        if (!process(current)) return false;

        if (current.isDirectory()) {
          final VirtualFile[] files = current.getChildren();
          if (myDirectoryFilter != null && ! myDirectoryFilter.process(current, files)) continue;

          for (VirtualFile child : files) {
            if (myRootPresentFilter != null && (! myRootPresentFilter.accept(child))) continue;
            if (isExcluded(myExcludedFileIndex, child)) continue;
            myQueue.add(child);
          }
        }
      }
      return true;
    }

    private boolean process(VirtualFile current) {
      if (myProcessor != null) {
        return myProcessor.process(new FilePathImpl(current));
      } else {
        return myVfProcessor.process(current);
      }
    }
  }
}
