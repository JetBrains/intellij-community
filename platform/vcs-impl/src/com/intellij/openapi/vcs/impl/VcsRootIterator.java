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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;

import java.util.*;

public class VcsRootIterator {
  // folder path to files to be excluded
  private final Map<String, MyRootFilter> myOtherVcsFolders;
  private final ExcludedFileIndex myExcludedFileIndex;

  public VcsRootIterator(final Project project, final AbstractVcs vcs) {
    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
    myOtherVcsFolders = new HashMap<String, MyRootFilter>();
    myExcludedFileIndex = ExcludedFileIndex.getInstance(project);

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
    if (myExcludedFileIndex.isExcludedFile(file)) return false;
    return true;
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
    final MyRootIterator rootIterator = new MyRootIterator(project, root, processor);
    return rootIterator.iterate();
  }

  private static class MyRootIterator {
    private final Processor<FilePath> myProcessor;
    private final LinkedList<VirtualFile> myQueue;
    private final MyRootFilter myRootPresentFilter;
    private final ExcludedFileIndex myExcludedFileIndex;

    private MyRootIterator(final Project project, final VirtualFile root, final Processor<FilePath> processor) {
      myProcessor = processor;

      final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
      final AbstractVcs vcs = plVcsManager.getVcsFor(root);
      myRootPresentFilter = (vcs == null) ? null : new MyRootFilter(root, vcs.getName());
      myExcludedFileIndex = ExcludedFileIndex.getInstance(project);

      myQueue = new LinkedList<VirtualFile>();
      myQueue.add(root);
    }

    public boolean iterate() {
      while (! myQueue.isEmpty()) {
        final VirtualFile current = myQueue.removeFirst();
        if (! myProcessor.process(new FilePathImpl(current))) return false;

        if (current.isDirectory()) {
          final VirtualFile[] files = current.getChildren();

          for (VirtualFile child : files) {
            if (myRootPresentFilter != null && (! myRootPresentFilter.accept(child))) continue;
            if (myExcludedFileIndex.isExcludedFile(child)) continue;
            myQueue.add(child);
          }
        }
      }
      return true;
    }
  }
}
