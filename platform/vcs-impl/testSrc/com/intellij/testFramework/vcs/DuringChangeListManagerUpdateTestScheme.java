// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.changes.committed.MockDelayingChangeProvider;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;

public class DuringChangeListManagerUpdateTestScheme {
  private final Project myProject;
  private final VirtualFile myVcsRoot;
  private final MockDelayingChangeProvider myChangeProvider;
  private final VcsDirtyScopeManager myDirtyScopeManager;
  private final ChangeListManagerImpl myClManager;

  /**
   * call in setUp
   */
  public DuringChangeListManagerUpdateTestScheme(final Project project, final String tmpDirPath) {
    myProject = project;
    final MockAbstractVcs vcs = new MockAbstractVcs(myProject);
    myChangeProvider = new MockDelayingChangeProvider();
    vcs.setChangeProvider(myChangeProvider);

    final File mockVcsRoot = new File(tmpDirPath, "mock");
    mockVcsRoot.mkdir();
    myVcsRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(mockVcsRoot);

    final ProjectLevelVcsManagerImpl projectLevelVcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(project);
    projectLevelVcsManager.registerVcs(vcs);
    //projectLevelVcsManager.setDirectoryMapping(mockVcsRoot.getAbsolutePath(), vcs.getName());
    final ArrayList<VcsDirectoryMapping> list =
      new ArrayList<>(projectLevelVcsManager.getDirectoryMappings());
    list.add(new VcsDirectoryMapping(myVcsRoot.getPath(), vcs.getName()));
    projectLevelVcsManager.setDirectoryMappings(list);

    AbstractVcs vcsFound = projectLevelVcsManager.findVcsByName(vcs.getName());
    final VirtualFile[] roots = projectLevelVcsManager.getRootsUnderVcs(vcsFound);
    assert roots.length == 1 : Arrays.asList(roots) + "; " + vcs.getName() + "; " + Arrays.toString(AllVcses.getInstance(project).getAll());

    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
    myClManager = ChangeListManagerImpl.getInstanceImpl(project);
  }

  public void doTest(final Runnable runnable) {
    final TimeoutWaiter waiter = new TimeoutWaiter();

    final DuringUpdateTest test = new DuringUpdateTest(waiter, runnable);
    myChangeProvider.setTest(test);
    waiter.setControlled(test);

    // prevent async refresh events from VcsDirtyScopeVfsListener
    VfsUtil.markDirtyAndRefresh(false, true, true, myVcsRoot);
    myProject.getService(VcsDirtyScopeVfsListener.class).waitForAsyncTaskCompletion();

    myDirtyScopeManager.markEverythingDirty();
    myClManager.ensureUpToDate();
    waiter.startTimeout();

    if (test.getException() != null) {
      test.getException().printStackTrace();
    }
    assert test.get() : (test.getException() == null ? null : test.getException().getMessage());
  }

  private class DuringUpdateTest implements Runnable, Supplier<Boolean> {
    private boolean myDone;
    private final TimeoutWaiter myWaiter;
    private final Runnable myRunnable;
    private Exception myException;

    protected DuringUpdateTest(final TimeoutWaiter waiter, final Runnable runnable) {
      myWaiter = waiter;
      myRunnable = runnable;
    }

    @Override
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

    @Override
    public Boolean get() {
      return myDone;
    }
  }

  public static void checkFilesAreInList(final VirtualFile[] files, final String listName, final ChangeListManager manager) {
    checkFilesAreInList(listName, manager, files);
  }

  public static void checkFilesAreInList(final String listName, final ChangeListManager manager, final VirtualFile... files) {
    assert manager.findChangeList(listName) != null : manager.getChangeLists();
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
      assert found : debugRealListContent(list);
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
      assert found : debugRealListContent(list);
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
