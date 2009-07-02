/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package git4idea.vfs;

import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerAdapter;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The component tracks Git roots for the project. If roots are mapped incorrectly it
 * shows balloon that notifies user about the problem and offers to correct root mapping.
 */
public class GitRootTracker implements VcsListener {
  /**
   * The context project
   */
  private final Project myProject;
  /**
   * The vcs manager that tracks content roots
   */
  private final ProjectLevelVcsManager myVcsManager;
  /**
   * The vcs instance
   */
  private final GitVcs myVcs;
  /**
   * If true, the tracking is enabled.
   */
  private final AtomicBoolean myIsEnabled = new AtomicBoolean(false);
  /**
   * If true, the root configuration has been possibly invalidated
   */
  private final AtomicBoolean myRootsInvalidated = new AtomicBoolean(true);
  /**
   * If true, there are some configured git roots, or listener has never been run yet
   */
  private final AtomicBoolean myHasGitRoots = new AtomicBoolean(true);
  /**
   * If true, the notification is currently active and has not been dismissed yet.
   */
  private final AtomicBoolean myNotificationPosted = new AtomicBoolean(false);
  /**
   * The invalid git roots
   */
  private static final String GIT_INVALID_ROOTS_ID = "GIT_INVALID_ROOTS";
  /**
   * The command listener
   */
  private CommandListener myCommandListener;
  /**
   * The file listener
   */
  private MyFileListener myFileListener;
  /**
   * Listener for refresh events
   */
  private VirtualFileManagerAdapter myVirtualFileManagerListener;
  /**
   * Local file system service
   */
  private LocalFileSystem myLocalFileSystem;
  /**
   * The multicaster for root events
   */
  private GitRootsListener myMulticaster;

  /**
   * The constructor
   *
   * @param project     the project instance
   * @param multicaster
   */
  public GitRootTracker(GitVcs vcs, @NotNull Project project, @NotNull GitRootsListener multicaster) {
    myMulticaster = multicaster;
    if (project.isDefault()) {
      throw new IllegalArgumentException("The project must not be default");
    }
    myProject = project;
    myVcs = vcs;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myVcsManager.addVcsListener(this);
    myLocalFileSystem = LocalFileSystem.getInstance();
    myCommandListener = new CommandAdapter() {
      @Override
      public void commandFinished(CommandEvent event) {
        if (!myRootsInvalidated.compareAndSet(true, false)) {
          return;
        }
        checkRoots(false);
      }
    };
    CommandProcessor.getInstance().addCommandListener(myCommandListener);
    myFileListener = new MyFileListener();
    VirtualFileManagerEx fileManager = (VirtualFileManagerEx)VirtualFileManager.getInstance();
    fileManager.addVirtualFileListener(myFileListener);
    myVirtualFileManagerListener = new VirtualFileManagerAdapter() {
      @Override
      public void afterRefreshFinish(boolean asynchonous) {
        if (!myRootsInvalidated.compareAndSet(true, false)) {
          return;
        }
        checkRoots(false);
      }
    };
    fileManager.addVirtualFileManagerListener(myVirtualFileManagerListener);
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
      public void run() {
        myIsEnabled.set(true);
        checkRoots(true);
      }
    });
  }

  /**
   * Dispose the component removing all related listeners
   */
  public void dispose() {
    myVcsManager.removeVcsListener(this);
    CommandProcessor.getInstance().removeCommandListener(myCommandListener);
    VirtualFileManagerEx fileManager = (VirtualFileManagerEx)VirtualFileManager.getInstance();
    fileManager.removeVirtualFileListener(myFileListener);
    fileManager.removeVirtualFileManagerListener(myVirtualFileManagerListener);
  }

  /**
   * {@inheritDoc}
   */
  public void directoryMappingChanged() {
    if (myProject.isDisposed()) {
      return;
    }
    checkRoots(true);
  }

  /**
   * Check roots for changes.
   *
   * @param rootsChanged
   */
  private void checkRoots(boolean rootsChanged) {
    if (!myIsEnabled.get() || (!rootsChanged && !myHasGitRoots.get())) {
      return;
    }
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        boolean hasInvalidRoots = false;
        HashSet<String> rootSet = new HashSet<String>();
        for (VcsDirectoryMapping m : myVcsManager.getDirectoryMappings()) {
          if (!m.getVcs().equals(myVcs.getName())) {
            continue;
          }
          String path = m.getDirectory();
          if (path.length() == 0) {
            VirtualFile baseDir = myProject.getBaseDir();
            assert baseDir != null;
            path = baseDir.getPath();
          }
          VirtualFile root = lookupFile(path);
          if (root == null) {
            hasInvalidRoots = true;
            break;
          }
          else {
            rootSet.add(root.getPath());
          }
        }
        if (!hasInvalidRoots && rootSet.isEmpty()) {
          myHasGitRoots.set(false);
          return;
        }
        else {
          myHasGitRoots.set(true);
        }
        if (!hasInvalidRoots) {
          // check if roots have a problem
          loop:
          for (String path : rootSet) {
            VirtualFile root = lookupFile(path);
            VirtualFile gitRoot = GitUtil.gitRootOrNull(root);
            if (gitRoot == null || hasUnmappedSubroots(root, rootSet)) {
              hasInvalidRoots = true;
              break;
            }
            for (String otherPath : rootSet) {
              if (otherPath.equals(path)) {
                continue;
              }
              if (otherPath.startsWith(path)) {
                VirtualFile otherFile = lookupFile(otherPath);
                if (otherFile == null) {
                  hasInvalidRoots = true;
                  break loop;
                }
                VirtualFile otherRoot = GitUtil.gitRootOrNull(otherFile);
                if (otherRoot == null || otherRoot == root || otherFile != otherRoot) {
                  hasInvalidRoots = true;
                  break loop;
                }
              }
            }
          }
        }
        if (!hasInvalidRoots) {
          // all roots are correct
          if (myNotificationPosted.compareAndSet(true, false)) {
            final Notifications notifications = myProject.getMessageBus().syncPublisher(Notifications.TOPIC);
            notifications.invalidateAll(GIT_INVALID_ROOTS_ID);
          }
          return;
        }
        if (myNotificationPosted.compareAndSet(false, true)) {
          String title = GitBundle.message("root.tracker.message");
          final Notifications notifications = myProject.getMessageBus().syncPublisher(Notifications.TOPIC);
          notifications.notify(GIT_INVALID_ROOTS_ID, title, title, NotificationType.ERROR, new NotificationListener() {
            @NotNull
            public Continue perform() {
              if (fixRoots()) {
                myNotificationPosted.set(false);
                return Continue.REMOVE;
              }
              else {
                return Continue.LEAVE;
              }
            }

            public Continue onRemove() {
              return Continue.LEAVE;
            }
          });
        }
      }
    });
    myMulticaster.gitRootsChanged();
  }

  /**
   * Check if there are some unmapped subdirectories under git
   *
   * @param directory the content root to check
   * @param rootSet   the mapped root set
   * @return true if there are unmapped subroots
   */
  private static boolean hasUnmappedSubroots(VirtualFile directory, HashSet<String> rootSet) {
    for (VirtualFile child : directory.getChildren()) {
      if (child.getName().equals(".git") || !child.isDirectory()) {
        continue;
      }
      if (child.findChild(".git") != null && !rootSet.contains(child.getPath())) {
        return true;
      }
      if (hasUnmappedSubroots(child, rootSet)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Fix mapped roots
   *
   * @return true if roots now in the correct state
   */
  boolean fixRoots() {
    final List<VcsDirectoryMapping> vcsDirectoryMappings = new ArrayList<VcsDirectoryMapping>(myVcsManager.getDirectoryMappings());
    final HashSet<String> mapped = new HashSet<String>();
    final HashSet<String> removed = new HashSet<String>();
    final HashSet<String> added = new HashSet<String>();
    final VirtualFile baseDir = myProject.getBaseDir();
    assert baseDir != null;
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (Iterator<VcsDirectoryMapping> i = vcsDirectoryMappings.iterator(); i.hasNext();) {
          VcsDirectoryMapping m = i.next();
          String vcsName = myVcs.getName();
          if (!vcsName.equals(m.getVcs())) {
            continue;
          }
          String path = m.getDirectory();
          if (path.length() == 0) {
            path = baseDir.getPath();
          }
          VirtualFile file = lookupFile(path);
          if (file != null && !mapped.add(file.getPath())) {
            // eliminate duplicates
            i.remove();
            continue;
          }
          if (file == null || GitUtil.gitRootOrNull(file) == null) {
            removed.add(path);
          }
        }
        for (String m : mapped) {
          VirtualFile file = lookupFile(m);
          if (file == null) {
            continue;
          }
          addSubroots(file, added, mapped);
          if (removed.contains(m)) {
            continue;
          }
          VirtualFile root = GitUtil.gitRootOrNull(file);
          assert root != null;
          for (String o : mapped) {
            // the mapped collection is not modified here, so order is being kept
            if (o.equals(m) || removed.contains(o)) {
              continue;
            }
            if (o.startsWith(m)) {
              VirtualFile otherFile = lookupFile(m);
              assert otherFile != null;
              VirtualFile otherRoot = GitUtil.gitRootOrNull(otherFile);
              assert otherRoot != null;
              if (otherRoot == root) {
                removed.add(o);
              }
              else if (otherFile != otherRoot) {
                added.add(otherRoot.getPath());
                removed.add(o);
              }
            }
          }
        }
      }
    });
    if (added.isEmpty() && removed.isEmpty()) {
      Messages.showInfoMessage(myProject, GitBundle.message("fix.roots.valid.message"), GitBundle.message("fix.roots.valid.title"));
      return true;
    }
    GitFixRootsDialog d = new GitFixRootsDialog(myProject, mapped, added, removed);
    d.show();
    if (!d.isOK()) {
      return false;
    }
    for (Iterator<VcsDirectoryMapping> i = vcsDirectoryMappings.iterator(); i.hasNext();) {
      VcsDirectoryMapping m = i.next();
      String path = m.getDirectory();
      if (removed.contains(path) || (path.length() == 0 && removed.contains(baseDir.getPath()))) {
        i.remove();
      }
    }
    for (String a : added) {
      vcsDirectoryMappings.add(new VcsDirectoryMapping(a, myVcs.getName()));
    }
    myVcsManager.setDirectoryMappings(vcsDirectoryMappings);
    myVcsManager.updateActiveVcss();
    return true;
  }

  /**
   * Look up file in the file system
   *
   * @param path the path to lookup
   * @return the file or null if the file not found
   */
  @Nullable
  private VirtualFile lookupFile(String path) {
    return myLocalFileSystem.findFileByPath(path);
  }

  /**
   * Add subroots for the content root
   *
   * @param directory the content root to check
   * @param toAdd     collection of roots to be added
   * @param mapped    all mapped git roots
   */
  private static void addSubroots(VirtualFile directory, HashSet<String> toAdd, HashSet<String> mapped) {
    for (VirtualFile child : directory.getChildren()) {
      if (!child.isDirectory()) {
        continue;
      }
      if (child.getName().equals(".git") && !mapped.contains(directory.getPath())) {
        toAdd.add(directory.getPath());
      }
      else {
        addSubroots(child, toAdd, mapped);
      }
    }
  }

  /**
   * The listener for git roots
   */
  private class MyFileListener extends VirtualFileAdapter {
    /**
     * Return true if file has git repositories
     *
     * @param file the file to check
     * @return true if file has git repositories
     */
    private boolean hasGitRepositories(VirtualFile file) {
      if (!file.isDirectory()) {
        return false;
      }
      if (file.getName().equals(".git")) {
        return true;
      }
      for (VirtualFile child : file.getChildren()) {
        if (hasGitRepositories(child)) {
          return true;
        }
      }
      return false;
    }

    /**
     * Invalidate git root
     */
    private void invalidate() {
      myRootsInvalidated.set(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fileCreated(VirtualFileEvent event) {
      if (!myHasGitRoots.get()) {
        return;
      }
      if (hasGitRepositories(event.getFile())) {
        invalidate();
      }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeFileDeletion(VirtualFileEvent event) {
      if (!myHasGitRoots.get()) {
        return;
      }
      if (hasGitRepositories(event.getFile())) {
        invalidate();
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
      if (!myHasGitRoots.get()) {
        return;
      }
      if (hasGitRepositories(event.getFile())) {
        invalidate();
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fileCopied(VirtualFileCopyEvent event) {
      if (!myHasGitRoots.get()) {
        return;
      }
      if (hasGitRepositories(event.getFile())) {
        invalidate();
      }
    }
  }
}
