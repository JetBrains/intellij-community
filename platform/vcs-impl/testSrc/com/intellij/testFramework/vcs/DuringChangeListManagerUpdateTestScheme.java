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
package com.intellij.testFramework.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.changes.committed.MockDelayingChangeProvider;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class DuringChangeListManagerUpdateTestScheme {
  private final MockDelayingChangeProvider myChangeProvider;
  private final VcsDirtyScopeManager myDirtyScopeManager;
  private final ChangeListManager myClManager;

  /**
   * call in setUp
   */
  public DuringChangeListManagerUpdateTestScheme(final Project project, final String tmpDirPath) {
    final MockAbstractVcs vcs = new MockAbstractVcs(project);
    myChangeProvider = new MockDelayingChangeProvider();
    vcs.setChangeProvider(myChangeProvider);

    final File mockVcsRoot = new File(tmpDirPath, "mock");
    mockVcsRoot.mkdir();
    final VirtualFile vRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(mockVcsRoot);

    final ProjectLevelVcsManagerImpl projectLevelVcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(project);
    projectLevelVcsManager.registerVcs(vcs);
    //projectLevelVcsManager.setDirectoryMapping(mockVcsRoot.getAbsolutePath(), vcs.getName());
    final ArrayList<VcsDirectoryMapping> list =
      new ArrayList<>(projectLevelVcsManager.getDirectoryMappings());
    list.add(new VcsDirectoryMapping(vRoot.getPath(), vcs.getName()));
    projectLevelVcsManager.setDirectoryMappings(list);

    AbstractVcs vcsFound = projectLevelVcsManager.findVcsByName(vcs.getName());
    final VirtualFile[] roots = projectLevelVcsManager.getRootsUnderVcs(vcsFound);
    assert roots.length == 1 : Arrays.asList(roots) + "; " + vcs.getName() + "; " + Arrays.toString(AllVcses.getInstance(project).getAll());

    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
    myClManager = ChangeListManager.getInstance(project);
  }

  public void doTest(final Runnable runnable) {
    final TimeoutWaiter waiter = new TimeoutWaiter();

    final DuringUpdateTest test = new DuringUpdateTest(waiter, runnable);
    myChangeProvider.setTest(test);
    waiter.setControlled(test);

    myDirtyScopeManager.markEverythingDirty();
    myClManager.ensureUpToDate(false);
    waiter.startTimeout();

    if (test.getException() != null) {
      test.getException().printStackTrace();
    }
    assert test.get() : (test.getException() == null ? null : test.getException().getMessage());
  }

  private class DuringUpdateTest implements Runnable, Getter<Boolean> {
    private boolean myDone;
    private final TimeoutWaiter myWaiter;
    private final Runnable myRunnable;
    private Exception myException;

    protected DuringUpdateTest(final TimeoutWaiter waiter, final Runnable runnable) {
      myWaiter = waiter;
      myRunnable = runnable;
    }

    public void run() {
      try {
        myRunnable.run();
      }
      catch (final Exception e) {
        myException = e;
      }

      myDone = myException == null;

      myChangeProvider.setTest(null);
      myChangeProvider.unlock();
      synchronized (myWaiter) {
        myWaiter.notifyAll();
      }
    }

    public Exception getException() {
      return myException;
    }

    public Boolean get() {
      return myDone;
    }
  }

  public static void checkFilesAreInList(final VirtualFile[] files, final String listName, final ChangeListManager manager) {
    checkFilesAreInList(listName, manager, files);
  }

  public static void checkFilesAreInList(final String listName, final ChangeListManager manager, final VirtualFile... files) {
    assert manager.findChangeList(listName) != null;
    final LocalChangeList list = manager.findChangeList(listName);
    final Collection<Change> changes = list.getChanges();
    assert changes.size() == files.length : "size: " + changes.size() + " " + debugRealListContent(list);

    for (Change change : changes) {
      final VirtualFile vf = change.getAfterRevision().getFile().getVirtualFile();
      boolean found = false;
      for (VirtualFile file : files) {
        if (file.equals(vf)) {
          found = true;
          break;
        }
      }
      assert found == true  : debugRealListContent(list);
    }
  }

  public static void checkDeletedFilesAreInList(final VirtualFile[] files, final String listName, final ChangeListManager manager) {
    assert manager.findChangeList(listName) != null;
    final LocalChangeList list = manager.findChangeList(listName);
    final Collection<Change> changes = list.getChanges();
    assert changes.size() == files.length : debugRealListContent(list);

    for (Change change : changes) {
      final File vf = change.getBeforeRevision().getFile().getIOFile();
      boolean found = false;
      for (VirtualFile vfile : files) {
        final File file = new File(vfile.getPath());
        if (file.equals(vf)) {
          found = true;
          break;
        }
      }
      assert found == true  : debugRealListContent(list);
    }
  }

  private static String debugRealListContent(final LocalChangeList list) {
    final StringBuilder sb = new StringBuilder(list.getName() + ": ");
    final Collection<Change> changeCollection = list.getChanges();
    for (Change change : changeCollection) {
      sb.append(change.toString()).append(' ');
    }
    return sb.toString();
  }
}
