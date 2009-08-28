package com.intellij.openapi.vcs.changes.pending;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.changes.committed.MockDelayingChangeProvider;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
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

    final ProjectLevelVcsManagerImpl projectLevelVcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(project);
    projectLevelVcsManager.registerVcs(vcs);
    projectLevelVcsManager.setDirectoryMapping(mockVcsRoot.getAbsolutePath(), vcs.getName());

    AbstractVcs vcsFound = projectLevelVcsManager.findVcsByName(vcs.getName());
    assert projectLevelVcsManager.getRootsUnderVcs(vcsFound).length == 1: "size: " + projectLevelVcsManager.getRootsUnderVcs(vcsFound).length;

    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
    myClManager = ChangeListManager.getInstance(project);
  }

  public void doTest(final Runnable runnable) {
    final TimeoutWaiter waiter = new TimeoutWaiter();

    final DuringUpdateTest test = new DuringUpdateTest(waiter, runnable);
    myChangeProvider.setTest(test);
    waiter.setControlled(test);

    System.out.println("Starting delayed update..");
    myDirtyScopeManager.markEverythingDirty();
    myClManager.ensureUpToDate(false);
    System.out.println("Starting timeout..");
    waiter.startTimeout();
    System.out.println("Timeout waiter completed.");

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
      System.out.println("DuringUpdateTest: before test execution");
      try {
        myRunnable.run();
      }
      catch (final Exception e) {
        myException = e;
      }

      System.out.println("DuringUpdateTest: setting done");
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

  private static class TimeoutWaiter {
    private Getter<Boolean> myControlled;
    private final static long ourTimeout = 5000;
    private final Object myLock;

    private TimeoutWaiter() {
      myLock = new Object();
    }

    public void setControlled(final Getter<Boolean> controlled) {
      myControlled = controlled;
    }

    public void startTimeout() {
      assert myControlled != null;

      final long start = System.currentTimeMillis();
      synchronized (myLock) {
        while (((System.currentTimeMillis() - start) < ourTimeout) && (! Boolean.TRUE.equals(myControlled.get()))) {
          try {
            myLock.wait(300);
          }
          catch (InterruptedException e) {
            //
          }
        }
      }
    }
  }

  public static void checkFilesAreInList(final VirtualFile[] files, final String listName, final ChangeListManager manager) {
    System.out.println("Checking files for list: " + listName);
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
    System.out.println("Checking files for list: " + listName);
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
